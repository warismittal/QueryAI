/** Question categories that make exploring data feel natural. Phrased for the bundled e-commerce dataset. */
export interface QuestionCategory {
  id: string;
  title: string;
  kicker: string;
  questions: string[];
}

export const QUESTION_CATEGORIES: QuestionCategory[] = [
  {
    id: 'best',
    title: 'Discover the Best',
    kicker: 'What is working',
    questions: [
      'Which product is performing the best?',
      'Who are my most valuable customers?',
      'Which month generated the highest revenue?',
      'Which product is loved the most?',
    ],
  },
  {
    id: 'lowest',
    title: 'Find the Lowest',
    kicker: 'What needs care',
    questions: [
      'Which product is struggling the most?',
      'Which country has the lowest sales?',
      'Which customers have not purchased recently?',
      'Where am I losing revenue?',
    ],
  },
  {
    id: 'trends',
    title: 'Understand the Trends',
    kicker: 'What is changing',
    questions: [
      'Why are sales declining?',
      'What changed compared with last month?',
      'Which products are growing the fastest?',
      'What is the most unexpected trend?',
    ],
  },
  {
    id: 'opportunities',
    title: 'Explore Opportunities',
    kicker: 'Where to go next',
    questions: [
      'Where can I grow the most?',
      'Which customers deserve more attention?',
      'What is my biggest growth opportunity?',
      'Which products should I promote?',
    ],
  },
  {
    id: 'compare',
    title: 'Compare & Discover',
    kicker: 'Side by side',
    questions: [
      'Which product performs better: laptops or smartphones?',
      'Which country is growing faster?',
      'How has my performance changed this year?',
    ],
  },
];
