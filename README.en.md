# StudyOnTV

An Android learning and screen-time app for tablets, phones and TVs. Tablets and phones use the Mobile APK; TVs use a separate lightweight TV APK. Both keep question banks, progress and usage records locally.

This project is linked with and endorses the [LINUX DO](https://linux.do) community.

[Download APK](https://github.com/chadyi/StudyOnTV/releases/latest) · [中文说明](./README.md) · [Installation](./docs/install.md)

## Learning

- Single choice, multiple choice, true/false, short answers and subjective questions.
- Offline LaTeX formulas through bundled KaTeX and fonts.
- Keyboard input, system speech-to-text and handwriting images.
- Round-based tasks, correct-answer streaks and unrestricted practice.
- Configurable rewards for questions rated 1–5 stars.
- Optional spaced repetition prioritizes due questions and keeps learning progress across restarts.
- Persistent extra-practice pool: mistakes add questions at a configurable threshold; correct extra-practice answers repay the pool. Restarting or changing the date does not erase the pool.

## Parent management

The controlled device hosts its own local HTTP panel. Parents connect over the same network and enter the management password set during initial setup. Tap the application title five times, or press D-pad Up five times, to reopen the password-protected connection screen.

The panel separates rules, question banks, grading and usage records. APIs require authentication as well; the former six-digit access code and unauthenticated question-bank access have been removed.

Subjective submissions can be graded by a parent, an authenticated Agent, or an optional OpenAI-compatible AI endpoint such as DeepSeek. Handwriting requires a vision-capable model or human review. Failed AI calls preserve the submission for later review. Enabling AI sends question and answer content to the configured provider.

[Question-bank and grading API](./docs/ai-api.md) · [Gameplay rules](./docs/gameplay.md) · [Example question bank](./data/banks/open-play.json)

## Screenshots

![Tablet learning](./docs/screenshots/tablet-quiz.png)
![Tablet handwriting](./docs/screenshots/tablet-handwriting.png)
![Parent review](./docs/screenshots/parent-review.png)
![Parent dashboard](./docs/screenshots/parent-desktop.png)
![TV learning](./docs/screenshots/tv-quiz.png)

## Screen-time limits

Native Android code validates answers and awards time. Actual foreground app use consumes the persisted balance; study, launcher, locked-screen and screen-off time do not. Limits include daily usage, daily rewarded rounds and allowed hours, including overnight windows.

Accessibility and background-running permissions are required. Android force-stop, uninstall, revoked permissions and manufacturer background restrictions can stop enforcement. System settings and emergency calling remain available.

## Community suggestions

Thanks to **xiuweng** on LINUX DO for mobile control, the hidden management entry and persistent extra practice; and to **free-swimming** for star difficulty and different time rewards.

## License

Source available for personal, non-commercial household use under [LICENSE](./LICENSE). The project is not MIT-licensed. Third-party components retain their own licenses; see [third-party notices](./THIRD_PARTY_NOTICES.md).
