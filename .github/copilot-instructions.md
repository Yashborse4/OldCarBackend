---
project: D:\Startup\Car Frontend Backend
generated: 2026-02-01
---

# GitHub Copilot Instructions

These instructions guide GitHub Copilot when generating code for this project.

## Project Overview

This is a java/typescript/kotlin/python/swift project

## Code Style

### TypeScript Rules

- Use explicit return types for all exported functions and public methods
- Prefer `interface` over `type` for object shapes that may be extended
- Use `unknown` instead of `any` and apply type guards for narrowing
- Use `const` assertions (`as const`) for literal types and readonly data
- Use discriminated unions for complex state with a shared `type` or `kind` field
- Enable and respect `strict` mode in tsconfig.json
- Use `readonly` for properties that should not be mutated
- Prefer `Record<K, V>` over `{ [key: string]: V }` for index signatures
- Use template literal types for string patterns when applicable
- Export types alongside their implementations for discoverability

### Python Rules

- Use type hints for all function parameters and return types
- Follow PEP 8 style guide for naming and formatting
- Use `pathlib.Path` instead of `os.path` for file path operations
- Use f-strings for string formatting
- Use context managers (`with` statement) for resource management
- Use list/dict/set comprehensions when they improve readability
- Use `dataclasses` or `pydantic` models for data structures
- Use `Enum` for fixed sets of values
- Use `logging` module instead of print statements in production code
- Use virtual environments for project dependencies

### Naming Conventions

- Files: `kebab-case.ts` for utilities, `PascalCase.tsx` for components
- Functions and variables: `camelCase`
- Constants: `SCREAMING_SNAKE_CASE`
- Types, Interfaces, and Classes: `PascalCase`
- Booleans: prefix with `is`, `has`, `can`, `should`
- Event handlers: prefix with `on` or `handle`
- Private class members: prefix with underscore `_` or use private keyword

### Error Handling Rules

- Use typed error classes extending Error for domain errors
- Always provide user-friendly error messages for UI display
- Log errors with context: request ID, user ID, action attempted
- Use Result/Either types for recoverable errors in critical paths
- Centralize error handling in middleware or error boundaries
- Distinguish between client errors (4xx) and server errors (5xx)

### Git & Version Control Rules

- Format commit messages: `type(scope): description`
- Commit types: feat, fix, docs, style, refactor, test, chore
- Keep commits atomic and focused on a single change
- Reference issue numbers in commit messages when applicable
- Write descriptive PR titles and descriptions
- Rebase feature branches before merging to main

### Security Rules

- Validate and sanitize all user inputs
- Use parameterized queries to prevent SQL injection
- Use HTTPS for all API communications
- Implement proper authentication and authorization
- Use environment variables for secrets, never hardcode
- Set proper CORS headers
- Use Content Security Policy headers
- Keep dependencies updated to patch security vulnerabilities

## Patterns to Avoid

- ❌ Use `@ts-ignore` or `@ts-expect-error` without a clear explanation
- ❌ Use `any` in function parameters, return types, or variable declarations
- ❌ Disable TypeScript strict mode or ESLint rules with inline comments
- ❌ Use non-null assertion (`!`) without documenting why it's safe
- ❌ Cast with `as` to silence the compiler instead of fixing types
- ❌ Use `Function` type - use specific function signatures instead
- ❌ Use `Object` or `{}` as types - use `Record<string, unknown>` or specific types
- ❌ Use bare `except:` clauses - always catch specific exceptions
- ❌ Use mutable default arguments (lists, dicts) in function definitions
- ❌ Use `from module import *` - import specific names
- ❌ Use `type()` for type checking - use `isinstance()` instead
- ❌ Use global variables for state management
- ❌ Use `print()` for error handling - raise exceptions instead
- ❌ Use abbreviations that are not universally understood
- ❌ Use single-letter variable names except in short scopes (loops)
- ❌ Mix naming conventions inconsistently
- ❌ Swallow errors silently without logging
- ❌ Expose stack traces or internal errors to users
- ❌ Use generic catch-all error messages
- ❌ Log sensitive user data in error logs
