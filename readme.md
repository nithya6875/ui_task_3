# Task Management Frontend

A React 19 + TypeScript frontend built with Ant Design for the Task Management API backend.

## Features

- **Task Management**: Create, read, update, delete tasks
- **Search**: Search tasks by name with real-time filtering
- **Execution**: Execute tasks and view output in a drawer
- **Responsive Design**: Mobile-friendly interface using Ant Design
- **Loading States**: Visual feedback for all operations
- **Error Handling**: User-friendly error messages and notifications
- **Accessibility**: WCAG compliant components

## Tech Stack

- **React 19** - Latest React with concurrent features
- **TypeScript** - Type safety and better developer experience
- **Ant Design 5** - Modern UI component library
- **Axios** - HTTP client for API communication
- **Vite** - Fast build tool and dev server

## Prerequisites

- Node.js 18+ 
- npm or yarn
- Backend API running on `http://localhost:8081`

## Setup Instructions

### 1. Install Dependencies

```bash
cd frontend
npm install
```

### 2. Start Development Server

```bash
npm run dev
```

The frontend will be available at `http://localhost:3000`
<img width="1316" height="505" alt="Screenshot 2025-10-20 203708" src="https://github.com/user-attachments/assets/0c12d463-2e17-4da7-9b65-ddca1f15b8c4" />


### 3. Build for Production

```bash
npm run build
```

## API Integration

The frontend communicates with the Spring Boot backend through a proxy configuration in `vite.config.ts`:

- Frontend requests to `/api/*` are proxied to `http://localhost:8081/*`
- This avoids CORS issues during development

## Features Overview

### Task Table
- Displays all tasks in a responsive table
- Columns: ID, Name, Owner, Command, Executions Count, Actions
- Pagination with customizable page sizes
- Command display with syntax highlighting

### Search Functionality
- Real-time search by task name
- Case-insensitive matching
- Clear search to show all tasks

### Create Task Modal
- Form validation for all fields
- Command field with textarea for multi-line commands
- Real-time validation feedback

### Task Execution
- Execute tasks with visual loading indicator
- Results displayed in a side drawer
- Shows execution time, duration, and output
- Output formatted with syntax highlighting

### Delete Confirmation
- Popconfirm dialog for safe deletion
- Prevents accidental task removal

## Component Structure

```
src/
├── App.tsx              # Main application component
├── main.tsx             # Application entry point
├── types/
│   └── task.ts          # TypeScript interfaces
├── services/
│   └── api.ts           # API service layer
└── index.css            # Global styles
```

## API Endpoints Used

- `GET /tasks` - Get all tasks
- `GET /tasks?id={id}` - Get task by ID
- `GET /tasks/search?name={name}` - Search tasks by name
- `PUT /tasks` - Create or update task
- `DELETE /tasks/{id}` - Delete task
- `PUT /tasks/{id}/execute` - Execute task

## Error Handling

- Network errors are caught and displayed as user-friendly messages
- Form validation prevents invalid submissions
- Loading states provide visual feedback during operations
- API errors are logged to console for debugging

## Responsive Design

- Mobile-first approach using Ant Design's responsive grid
- Table scrolls horizontally on small screens
- Drawer adapts to screen size
- Touch-friendly button sizes

## Accessibility

- Semantic HTML structure
- ARIA labels and roles
- Keyboard navigation support
- Screen reader compatibility
- High contrast color scheme

## Development

### Available Scripts

- `npm run dev` - Start development server
- `npm run build` - Build for production
- `npm run preview` - Preview production build
- `npm run lint` - Run ESLint

### Code Style

- TypeScript strict mode enabled
- ESLint configuration for React and TypeScript
- Consistent code formatting
- Component-based architecture

## Troubleshooting

### Common Issues

1. **Backend not running**: Ensure Spring Boot API is running on port 8081
2. **CORS errors**: The Vite proxy should handle this automatically
3. **Build errors**: Check Node.js version (18+ required)
4. **Type errors**: Ensure all TypeScript interfaces match backend models

### Debug Mode

Enable debug logging by opening browser dev tools and checking the console for API request/response logs.

## License

MIT
