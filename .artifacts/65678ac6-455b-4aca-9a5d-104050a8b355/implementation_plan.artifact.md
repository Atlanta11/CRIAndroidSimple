# Implementation Plan - Fix CRI Robot Control App

This plan addresses threading deadlocks, protocol errors, and UI usability issues in the CRI Robot Control application.

## User Review Required

> [!NOTE]
> The threading model will be updated to use a thread pool. This ensures that the background reader doesn't block outgoing commands.

## Proposed Changes

### [Resources]

#### [NEW] [strings.xml](file:///C:/Users/Intel/Downloads/CRIAndroidSimple/app/src/main/res/values/strings.xml)
Extract all hardcoded strings from the layout for better maintainability and to follow Android standards.

### [Layout]

#### [MODIFY] [activity_main.xml](file:///C:/Users/Intel/Downloads/CRIAndroidSimple/app/src/main/res/layout/activity_main.xml)
- Replace hardcoded strings with references to `@string/`.
- Set `android:baselineAligned="false"` on horizontal layouts for performance.
- Add `android:scrollbars="vertical"` to the log view (already present, but ensures it's configured correctly).

### [Code]

#### [MODIFY] [MainActivity.kt](file:///C:/Users/Intel/Downloads/CRIAndroidSimple/app/src/main/java/com/example/criandroidsimple/MainActivity.kt)
- **Threading**: Switch from `SingleThreadExecutor` to `CachedThreadPool` to prevent the reader loop from blocking the command sender.
- **Protocol**: Remove duplicate `CMD` prefixing. The `send()` function will handle the wrapper, and callers will provide the command string.
- **Reader**: Implement a more robust buffer handling that doesn't lose data arriving after the `CRIEND` terminator.
- **Logs**: Enable scrolling for `logText` and implement auto-scroll to the bottom.
- **Error Handling**: Add basic null checks and improve disconnection logic.

## Verification Plan

### Manual Verification
1. Deploy to device/emulator.
2. Verify that clicking "CONNECT" doesn't freeze the app.
3. Verify that "TX" logs show correctly formatted `CRISTART ... CRIEND` messages without double `CMD`.
4. Verify that receiving data (mocked or real) updates the log and it scrolls automatically.
5. Verify that "DISCONNECT" properly closes the socket and stops the reader.
