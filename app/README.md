Build a production-ready mobile application called “BrightAlarm” for Android and iOS.

Goal:
Create a reminder app that helps users remember tasks using:
1. Time-based alarms
2. Location-based reminders

Core Feature 1: Time-Based Alarm Reminder
The app must allow users to:
- Set an alarm for a specific date and time
- Choose AM/PM format
- Enter a custom reminder text message
- Use that entered text as the alarm voice/audio message, like a traditional alarm but personalized
- Optionally choose a default alarm tone or custom sound
- Repeat alarms with options:
    - One time
    - Daily
    - Weekdays
    - Weekends
    - Custom selected days
- Edit, pause, snooze, delete, and reschedule alarms
- Show active/inactive alarm list clearly

Alarm behavior requirements:
- Alarm must ring even if the app is closed
- Alarm must ring when the app is in background
- Alarm must still trigger after device restart
- Alarm notification must be high priority and hard to miss
- Support vibration, full-screen alert, and snooze
- Custom text should be converted to speech and played when the alarm rings
- If custom voice generation is not possible offline, use device text-to-speech engine reliably

Core Feature 2: Location-Based Reminder
The app must allow users to:
- Pick a location from map
- Save a reminder linked to that location
- Enter a custom reminder message
- Optionally attach a sound/alarm tone
- Choose trigger distance such as:
    - 500 meters
    - 1 km
    - 2 km
    - 5 km
    - Custom radius
- Trigger reminder when the user is approaching the saved location
- Use GPS/location tracking efficiently with battery optimization
- Support multiple saved location reminders
- Allow entering and exiting geofence logic if needed

Location reminder behavior requirements:
- Reminder should work even when the app is not open
- Use background location tracking / geofencing properly
- Trigger local notification and optional alarm sound
- Show map preview and saved place details
- Handle permission denial gracefully

Important Functional Requirements:
- Clean and simple mobile-first UI
- Home screen with:
    - Upcoming alarms
    - Upcoming location reminders
    - Quick add button
- Alarm creation screen
- Location reminder creation screen with map picker
- Reminder history/log screen
- Settings screen for:
    - Alarm sound
    - Voice/TTS language
    - Notification preferences
    - Default snooze duration
    - Battery optimization guidance

Technical Requirements:
- Use an industry-standard architecture
- Must be scalable, maintainable, and production-ready
- Should handle offline usage where possible
- Must store reminders locally and sync-ready for future cloud support
- Use background services, scheduled notifications, geofencing, and device restart recovery
- Strong focus on reliability of alarm triggering
- Avoid fake implementations; use implementable mobile APIs only

Preferred output:
Provide:
1. Recommended tech stack
2. App architecture
3. Feature breakdown
4. Database design
5. API/service layer design if needed
6. Required mobile permissions
7. Background execution strategy
8. Best packages/libraries
9. Edge cases
10. Step-by-step implementation plan

Important:
This is not a basic demo app. Design it like a real product that users can depend on daily for task reminders and location-based alerts.