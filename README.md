# CRIAndroidSimple

A simple Android application for controlling an igus ReBeL robot using the CRI communication protocol.

## Features

* Connect / Disconnect
* Automatic reconnect when the TCP connection is lost
* Connection status and information
* TCP communication with the robot controller
* Send CRI commands
* Load programs
* Start programs
* Stop programs
* Pause programs
* Program selection
* Operator / Expert mode
* Configurable program buttons
* Log / communication monitor
* Dutch text-to-speech feedback

## Program Control

The application is designed to prevent programs from being started in the wrong sequence.

* A program must be **fully completed before the next program can start**.
* The application monitors the program status received from the robot controller.
* A new program is only started when the previous program has finished.
* This helps prevent multiple programs from running at the same time.
* Programs can still be stopped or paused when required.
* The current program and communication status can be monitored through the log.

## Automatic Reconnect

The application includes an **automatic reconnect function**.

When the TCP connection to the robot controller is lost:

1. The application detects the lost connection.
2. The connection status is updated.
3. The application automatically attempts to reconnect.
4. Once the connection is restored, the robot communication is initialized again.
5. The application can continue communicating with the robot.

This is useful when the network connection is temporarily interrupted.

## Communication

* Protocol: **CRI**
* Connection type: **TCP/IP**
* Default TCP port: **3920**

The application communicates directly with the robot controller over the network.

## Requirements

* Android Studio
* Android device or emulator
* Network connection to the robot controller
* igus ReBeL robot with CRI interface

## Installation

1. Clone or download this repository.
2. Open the project folder in Android Studio.
3. Let Android Studio perform the Gradle sync.
4. Connect an Android device or start an emulator.
5. Build and run the application.

## Configuration

Enter the IP address of the robot controller and the TCP port in the application.

Default port:

`3920`

Make sure the Android device and robot controller are connected to the same network.

## Operator and Expert Mode

The application supports different access levels:

### Operator

Provides a simplified interface for normal operation.

### Expert

Provides access to additional controls and configuration options.

The available program buttons and controls can be configured according to the selected access level.

## Logging

The application includes a communication log for monitoring the connection and CRI communication.

The log can be used to troubleshoot:

* Connection problems
* Reconnect attempts
* Robot communication
* Program commands
* Program status
* Errors and responses

## Project

CRIAndroidSimple is intended as a lightweight Android-based CRI client and control interface for the igus ReBeL robot.

The project is designed to provide a simple touchscreen interface while maintaining direct TCP/IP communication with the robot controller.

## License

This project is provided for personal and development use.

Open the folder in Android Studio and let Gradle sync.
<img width="1490" height="856" alt="Schermafbeelding 2026-08-29 223216" src="https://github.com/user-attachments/assets/a78026d2-c39f-417a-93a7-d4d12f415df6" />


<img width="1489" height="856" alt="Schermafbeelding 2026-08-29 223235" src="https://github.com/user-attachments/assets/fe269a65-7631-44d7-9b7a-9149fb5a28ea" />


<img width="1492" height="857" alt="Schermafbeelding 2026-08-29 223249" src="https://github.com/user-attachments/assets/501ad97e-be58-4cd2-a83d-a8e6b24fdab4" />


<img width="1491" height="855" alt="Schermafbeelding 2026-08-29 223343" src="https://github.com/user-attachments/assets/954afb99-0093-450a-ac42-9fba496f0ca3" />


<img width="1488" height="858" alt="Schermafbeelding 2026-08-29 223411" src="https://github.com/user-attachments/assets/8a506bcd-4340-412a-b768-4ed72e671bcb" />




