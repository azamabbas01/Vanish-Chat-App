# Vanish Chat App

# A Real-Time Android Messaging Application with Disappearing Messages





Vanish Chat App is an Android based messaging application developed to provide real time communication through a client server architecture. The project uses WebSocket based communication and a dedicated relay server to support message exchange between connected devices.



The application focuses on temporary messaging, with messages designed to disappear after a defined period. The project also explores reliable communication across different networks through the Vanish Relay Protocol v1.



This project was developed as a practical implementation of mobile application development, networking, client server communication, and real time messaging concepts.



Table of Contents



1\. Project Objectives

2\. Key Features

3\. Technology Stack

4\. System Architecture

5\. Installation and Setup

6\. How to Run the Android App

7\. Server Setup

8\. Protocol Documentation

9\. Testing

10\. Future Improvements

11\. Contributors



\## 1. Project Objectives



The main objective of Vanish Chat App is to develop a real time Android messaging application that demonstrates practical implementation of modern mobile communication technologies.



The key objectives are:



\- To develop an Android application that supports real time message exchange.

\- To implement WebSocket based communication between clients and a server.

\- To develop and integrate a relay server for communication between connected devices.

\- To implement temporary messages that expire after a specified duration.

\- To explore communication between devices operating on different networks.

\- To implement client side handling of connectivity interruptions and reconnection.

\- To understand and apply client server architecture in a practical mobile application.

\- To design and document a communication protocol for message relay.



\## 2. Key Features



\### Real Time Messaging



The application uses WebSocket based communication to support real time message transmission between connected clients.



\### Disappearing Messages



Messages are designed to expire automatically after 30 seconds, supporting the application's temporary messaging approach.



\### Relay Based Communication



A relay server facilitates communication between connected devices without requiring them to communicate directly over the same local network.



\### Cross Network Communication



The relay based architecture is designed to support communication between devices connected through different networks.



\### Connection Handling



The application includes connection handling functionality to manage communication interruptions and reconnect when connectivity is restored.



\### Offline Message Handling



The application includes client side handling for messages when connectivity is unavailable. The relay itself does not maintain an offline message queue.



\### Custom Communication Protocol



The project introduces the Vanish Relay Protocol v1 to define the communication approach between the Android application and the relay server.



\## 3. Technology Stack



The project combines Android development technologies with server side programming and real time communication.



| Technology | Purpose |

|---|---|

| Kotlin | Android application development |

| Android SDK | Android platform and application components |

| Android Studio | Development environment |

| Python | Server side implementation |

| WebSocket | Real time communication |

| Vanish Relay Protocol v1 | Application level relay communication protocol |

| Git | Version control |

| GitHub | Source code hosting and project management |



\## 4. System Architecture



Vanish Chat App follows a client server architecture in which Android clients communicate through a WebSocket relay server.



\### Architecture Overview



Android Client A



Vanish Chat App



WebSocket Connection



Relay Server



Python Server



Vanish Relay Protocol v1



WebSocket Connection



Android Client B



Vanish Chat App



\### Architecture Components



1\. Android Client



The Android application provides the user facing messaging interface. It manages user interactions and communicates with the relay server.



2\. Relay Server



The Python based relay server handles connected clients and facilitates message forwarding through WebSocket communication.



3\. WebSocket Communication



WebSocket provides a persistent communication channel between the Android application and the relay server, allowing real time message exchange.



4\. Message Expiration



The application implements a 30 second message expiration approach for temporary messages.



5\. Client Connectivity Handling



The client handles connection interruptions and reconnection, while offline message handling is managed on the application side.



\## 5. Installation and Setup



Follow these steps to download and prepare the project for local development.



\### Prerequisites



Before starting, make sure the following software is installed:



\- Android Studio

\- Android SDK

\- Kotlin support through Android Studio

\- Python 3

\- Git

\- A physical Android device or Android Emulator



\### Clone the Repository



Open a terminal and execute:



```bash

git clone https://github.com/azamabbas01/Vanish-Chat-App.git

```



Navigate to the project directory:



```bash

cd Vanish-Chat-App

```



\### Project Structure



The repository contains the Android application and server side implementation.



```text

Vanish-Chat-App/



app/

&#x20;   Android application source code



server/

&#x20;   Python server implementation



PROTOCOL.md



VANISH\_GUIDE.md



README.md



.gitignore

```



The PROTOCOL.md file contains protocol related documentation, while VANISH\_GUIDE.md provides additional project guidance.



\## 6. How to Run the Android App



Follow these steps to open and run the Android application.



1\. Open Android Studio.

2\. Select Open from the welcome screen.

3\. Navigate to the cloned Vanish-Chat-App project.

4\. Open the Android project or module located in the app directory, if Android Studio does not automatically identify it.

5\. Allow Android Studio to complete Gradle synchronization.

6\. Resolve any SDK configuration requirements displayed by Android Studio.

7\. Connect an Android device using USB debugging or start an Android Emulator.

8\. Select the appropriate device from the device selector.

9\. Click the Run button.

10\. Wait for the application to build and install.

11\. Launch Vanish Chat App on the device.



\### Important Configuration



Make sure the application is configured to connect to the correct relay server address.



For an Android Emulator, remember that network addresses and local server access can differ from those used by a physical Android device.



If the server is hosted remotely, use the appropriate server endpoint configured for the project.



\## 7. Server Setup



The project includes a Python based server responsible for relaying messages between connected Android clients.



\### Step 1: Install Python



Install Python 3 and verify the installation:



```bash

python --version

```



\### Step 2: Navigate to the Server Directory



```bash

cd server

```



\### Step 3: Create a Virtual Environment



```bash

python -m venv .venv

```



Activate the environment on Windows:



```bash

.venv\\Scripts\\activate

```



On Linux or macOS:



```bash

source .venv/bin/activate

```



\### Step 4: Install Dependencies



If the server directory contains a requirements.txt file, install its dependencies:



```bash

pip install -r requirements.txt

```



If dependencies are documented separately, install the packages listed in the project's server documentation.



\### Step 5: Configure the Server



Review the server configuration and ensure that the application is using the correct WebSocket endpoint.



\### Step 6: Start the Server



Run the server using its actual Python entry point file and the command specified by the project configuration.



For example, if the entry point file is named main.py:



```bash

python main.py

```



The server should start and become available at its configured address and port.



Note: The exact entry point filename and port depend on the server implementation included in the repository.



\## 8. Protocol Documentation



\### Vanish Relay Protocol v1



Vanish Chat App uses the Vanish Relay Protocol v1 to define communication between the Android clients and the relay server.



The protocol is designed to facilitate real time message relay through WebSocket connections.



\### Protocol Overview



\- Protocol Name: Vanish Relay Protocol

\- Version: v1

\- Communication: WebSocket

\- Connection Type: Persistent WebSocket connection

\- Message Expiration: 30 seconds

\- Relay Storage: In memory relay handling

\- Offline Relay Queue: Not supported

\- Client Offline Handling: Managed by the Android application



\### Message Relay Process



1\. An Android client establishes a WebSocket connection with the relay server.

2\. The client sends a message through the established connection.

3\. The relay server processes the incoming communication.

4\. The relay forwards the message to the intended connected client.

5\. The application applies the message expiration behavior.

6\. Messages are intended to remain temporary rather than being retained indefinitely.



\### Message Expiration



The application follows a 30 second expiration period for messages. This is a core part of the temporary messaging design.



\### Offline Communication



The relay server does not maintain an offline message queue. Offline message handling is implemented on the client side.



\### Security Considerations



The current protocol implementation does not provide authentication at the relay level. The relay should therefore be treated as an unauthenticated communication endpoint.



The use of a secure WebSocket connection using wss protects the connection in transit, but it should not be confused with end to end encryption.



Further protocol security improvements are planned for future development.



For additional technical details, refer to:



\- PROTOCOL.md

\- VANISH\_GUIDE.md



\## 9. Testing



Testing was performed during the development of Vanish Chat App to evaluate the application's functionality, communication behavior, and integration with the relay server.



The testing activities were carried out by Azam Abbas, individual developer, and Seemab Zahra.



\### Testing Activities



The following areas were included in the project's testing process:



\- Application Launch: Verifying that the Android application launches successfully.

\- User Interface: Checking the application's screens and user interactions.

\- WebSocket Connection: Checking connectivity between the Android client and relay server.

\- Real Time Messaging: Testing message transmission and reception.

\- Message Expiration: Checking the 30 second message expiration behavior.

\- Relay Communication: Testing message forwarding through the relay server.

\- Network Connectivity: Checking communication under different network conditions.

\- Reconnection: Checking the application's connection recovery behavior.

\- Offline Handling: Checking client side handling when connectivity is unavailable.

\- Application Integration: Checking the interaction between the Android application and Python server.



\### Testing Team



Azam Abbas: Individual developer and tester.



Seemab Zahra: Testing and project verification.



Testing was performed as part of the project's development and verification process.



\## 10. Future Improvements



Although the current version implements the core messaging and relay functionality, several improvements can be considered for future development.



\- User Authentication: Introduce secure user authentication and client verification.

\- End to End Encryption: Implement end to end encryption to improve message confidentiality.

\- Improved Protocol Security: Add authentication, authorization, and stronger connection validation to the relay protocol.

\- Enhanced Message Delivery: Improve message delivery guarantees and handling of network interruptions.

\- Group Messaging: Extend the application to support group conversations.

\- Media Sharing: Add support for images, documents, and other media.

\- Improved User Interface: Enhance the design and usability of the application.

\- Delivery Status: Introduce message delivery and read indicators.

\- Notification Support: Add notifications for incoming messages.

\- Automated Testing: Introduce automated unit and integration tests.

\- Performance Optimization: Improve scalability and communication efficiency.



\## 11. Contributors



This project was developed and tested through individual development work and project collaboration.



\### Development



Azam Abbas



\- Android application development

\- Server side implementation and integration

\- WebSocket communication

\- Vanish Relay Protocol v1 implementation

\- Project integration and documentation

\- Git and GitHub project management



\### Testing



Azam Abbas: Individual testing and verification.



Seemab Zahra: Testing and project verification.



\## Project Repository



GitHub: https://github.com/azamabbas01/Vanish-Chat-App



Default Branch: main



Vanish Chat App is a practical implementation of real time Android messaging, WebSocket communication, and temporary message delivery.

