# RIMeet product memory

## Typography standard (do not change without explicit approval)

- **Manrope** is the only UI/body font. Use it for navigation, buttons, inputs, prose, headings, and all interactive controls.
- **DM Mono** is reserved for compact metadata: eyebrow labels, status, timestamps, version strings, badges, and technical identifiers.
- Both fonts are packaged locally in `desktop/src/assets/fonts`; do not introduce remote font loading or substitute a system font as a primary typeface.
- New controls must inherit the UI font through the global typography token. Use `--font-ui` and `--font-meta` rather than hard-coded font stacks.

## Visual standard

- Warm cream / soft orange neumorphism is the approved visual direction.
- Keep all media local by default. Transcript text may only go to Codex after an explicit user action.
