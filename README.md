# Break The Snooze

Break The Snooze is an Android alarm app that helps you actually get out of bed.

Instead of letting you dismiss alarms with a single tap, the app requires you to complete a short challenge before the alarm can be stopped. The goal is simple: reduce snoozing, fight sleep inertia, and make waking up more intentional.

## Why?

Traditional alarm apps are good at making noise, but not always good at making you awake.  
Break The Snooze is built around the idea that a small physical or cognitive task can help you transition from half-asleep to actually alert. The app combines reliable alarm scheduling with interactive wake-up flows designed to keep users engaged in the first minutes of the morning.

## Features

- Create and manage alarms
- Configure repeat days, labels, and dismissal tasks
- Use challenge-based alarm dismissal instead of instant snoozing
- Support quieter wake-up flows with Wear OS integration
- Fall back to phone audio when watch-based wake-up is not available
- Designed with a clean, low-friction mobile UI in mind

## Alarm Dismissal Tasks

To stop an alarm, the user must complete a task. The project supports multiple interaction styles, including:

- **Object recognition** using the camera
- **QR / barcode scanning**
- **Math challenges**
- **Memory-based tasks**
- **Fallback interaction flow** for more recoverable alarm handling

The object-recognition flow was built around live camera input, and the implementation explored ML-based approaches including ML Kit and TensorFlow-based prototyping. The final app integrates object detection in the wake-up experience, with toothbrush recognition used as one concrete scenario during development.

## Wear OS Support

Break The Snooze includes a companion flow for Wear OS devices.

If a connected and worn smartwatch is available, the wake-up process can start on the watch using vibration instead of immediately playing sound on the phone. This makes the initial alarm less disruptive for people nearby, while still keeping the alarm reliable. If the watch is unavailable, not worn, or no response arrives in time, the phone falls back to its normal audio-based alarm flow. Communication between phone and watch is handled in real time through a dedicated messaging layer.

## How It Works

At a high level:

1. An alarm is scheduled with Android's alarm system.
2. When it fires, the app launches the wake-up flow.
3. If a suitable Wear OS device is connected, the watch can handle the first alert phase.
4. The phone waits for acknowledgment and task start events.
5. If needed, the system falls back to phone audio.
6. The alarm is only considered dismissed once the required task is completed.

The scheduling layer is based on exact alarm handling, while the alarm trigger leads into a broader task-management process rather than just playing a sound.

## Tech Stack

- **Kotlin**
- **Android SDK**
- **Jetpack Compose**
- **Dagger Hilt**
- **ML Kit**
- **AlarmManager**
- **Wear OS messaging APIs**
- **Git + GitHub**
- **GitHub Actions** for CI/CD

## Architecture

The app is built around a modular Android architecture with clear responsibilities for:

- alarm scheduling
- wake-up flow orchestration
- task handling
- persistence
- phone/watch communication
- UI state management

A key part of the system is that the alarm trigger is not treated as the end of the story — it starts a controlled interaction flow that keeps the alarm active until the user meaningfully engages with it.

## Testing

The project includes test coverage for alarm scheduling and alarm-state consistency, including scenarios such as:

- alarms missing from the expected runtime state
- one-time alarms scheduled in the past
- repeated alarms on selected weekdays
- correct calculation of the next trigger time

These cases were used to verify that the scheduling logic behaves predictably across different user configurations.

## Project Goals

This project focuses on making alarms more effective, not just louder.

The main ideas behind Break The Snooze are:

- reduce overuse of snooze
- support better morning activation
- create a less disruptive wake-up experience
- combine usability with stricter alarm dismissal logic

## Roadmap Ideas

Some future directions mentioned during the project include:

- smarter personalization
- more wake-up task types
- habit and motivation features
- AI-assisted recommendations based on user behavior and sleep patterns

## Status

Break The Snooze is a functional Android application project with alarm management, task-based dismissal, smartwatch-assisted wake-up flow, and a CI-oriented development setup.

## Contributing

Contributions, ideas, and feedback are welcome.

If you'd like to improve the wake-up challenges, UI, Wear OS flow, or alarm reliability, feel free to open an issue or submit a pull request.
