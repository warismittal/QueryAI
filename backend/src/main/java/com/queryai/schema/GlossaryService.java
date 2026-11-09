package com.queryai.schema;

import com.queryai.audit.AuditAction;
import com.queryai.audit.AuditService;
import com.queryai.common.ApiException;
import com.queryai.datasource.DataSourceAccess;
import com.queryai.datasource.DataSourceEntity;
import com.queryai.schema.SchemaDtos.GlossaryTermDto;
import com.queryai.schema.SchemaDtos.GlossaryTermRequest;
import com.queryai.security.CurrentUser;
import com.queryai.workspace.WorkspaceRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class GlossaryService {
    private final GlossaryTermRepository repository;
    private final DataSourceAccess access;
    private final CurrentUser currentUser;
    private final AuditService audit;

    public GlossaryService(GlossaryTermRepository repository, DataSourceAccess access, CurrentUser currentUser, AuditService audit) {
        this.repository = repository;
        this.access = access;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<GlossaryTermDto> list(Long dataSourceId) {
        access.requireVisible(dataSourceId);
        return repository.findByDataSourceIdOrderByTerm(dataSourceId).stream().map(GlossaryTermDto::from).toList();
    }

    /** Internal use by the AI pipeline (authorisation already checked by the caller). */
    @Transactional(readOnly = true)
    public List<GlossaryTerm> terms(Long dataSourceId) {
        return repository.findByDataSourceIdOrderByTerm(dataSourceId);
    }

    @Transactional
    public GlossaryTermDto create(Long dataSourceId, GlossaryTermRequest request) {
        DataSourceEntity ds = access.requireQueryable(dataSourceId, WorkspaceRole.ANALYST);
        if (repository.existsByDataSourceIdAndTermIgnoreCase(dataSourceId, request.term().trim())) {
            throw ApiException.conflict("Glossary term '" + request.term() + "' already exists");
        }
        GlossaryTerm term = new GlossaryTerm();
        term.setDataSourceId(dataSourceId);
        term.setCreatedBy(currentUser.id());
        apply(term, request);
        repository.save(term);
        audit.record(AuditAction.GLOSSARY_UPDATED, ds.getWorkspaceId(), "GLOSSARY_TERM", term.getId(), Map.of("term", term.getTerm(), "op", "create"));
        return GlossaryTermDto.from(term);
    }

    @Transactional
    public GlossaryTermDto update(Long dataSourceId, Long termId, GlossaryTermRequest request) {
        DataSourceEntity ds = access.requireQueryable(dataSourceId, WorkspaceRole.ANALYST);
        GlossaryTerm term = load(dataSourceId, termId);
        apply(term, request);
        audit.record(AuditAction.GLOSSARY_UPDATED, ds.getWorkspaceId(), "GLOSSARY_TERM", termId, Map.of("term", term.getTerm(), "op", "update"));
        return GlossaryTermDto.from(term);
    }

    @Transactional
    public void delete(Long dataSourceId, Long termId) {
        DataSourceEntity ds = access.requireQueryable(dataSourceId, WorkspaceRole.ANALYST);
        GlossaryTerm term = load(dataSourceId, termId);
        repository.delete(term);
        audit.record(AuditAction.GLOSSARY_UPDATED, ds.getWorkspaceId(), "GLOSSARY_TERM", termId, Map.of("term", term.getTerm(), "op", "delete"));
    }

    private GlossaryTerm load(Long dataSourceId, Long termId) {
        GlossaryTerm term = repository.findById(termId).orElseThrow(() -> ApiException.notFound("Glossary term"));
        if (!term.getDataSourceId().equals(dataSourceId)) {
            throw ApiException.notFound("Glossary term");
        }
        return term;
    }

    private static void apply(GlossaryTerm term, GlossaryTermRequest r) {
        term.setTerm(r.term().trim());
        term.setDefinition(r.definition().trim());
        term.setSqlHint(r.sqlHint() == null || r.sqlHint().isBlank() ? null : r.sqlHint().trim());
    }
}
