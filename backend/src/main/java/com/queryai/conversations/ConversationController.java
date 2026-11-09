package com.queryai.conversations;

import com.queryai.conversations.ConversationService.ConversationDetail;
import com.queryai.conversations.ConversationService.ConversationDto;
import com.queryai.conversations.ConversationService.RenameRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/conversations")
@Tag(name = "Conversations")
public class ConversationController {
    private final ConversationService service;

    public ConversationController(ConversationService service) {
        this.service = service;
    }

    @GetMapping
    public List<ConversationDto> list(@RequestParam Long workspaceId, @RequestParam(required = false) Long dataSourceId) {
        return service.list(workspaceId, dataSourceId);
    }

    @GetMapping("/{id}")
    public ConversationDetail get(@PathVariable Long id) {
        return service.get(id);
    }

    @PatchMapping("/{id}")
    public ConversationDto rename(@PathVariable Long id, @Valid @RequestBody RenameRequest request) {
        return service.rename(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
