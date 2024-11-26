# Smart Delivery Box Backend

This repository contains the backend for the Smart Delivery Box application. The backend is responsible for handling various services such as user authentication, delivery management, and notification services.

## Table of Contents

- [Prerequisites](#prerequisites)
- [Setup Instructions](#setup-instructions)
- [Build and Run](#build-and-run)
- [Contributing](#contributing)

## Prerequisites

Ensure that the following software is installed on your machine:

- [Java 11+](https://openjdk.java.net/install/)
- [Gradle](https://gradle.org/install/)
- [Git](https://git-scm.com/)

## Setup Instructions

1. Clone this repository to your local machine:
    ```bash
    git clone https://github.com/saadahmad-1/sdb-backend.git
    ```
2. Navigate to the project directory:
    ```bash
    cd sdb-backend
    ```

3. Install dependencies (if necessary):
    ```bash
    ./gradlew build
    ```

## Build and Run

To build and run the application:

1. Clean and build the project:
    ```bash
    ./gradlew clean build
    ```

2. Run the project:
    ```bash
    ./gradlew run
    ```

Your backend service will now be running on the default port specified in your configuration (e.g., `localhost:8080`).

## Contributing

If you wish to contribute to this project:

1. Fork the repository.
2. Create a new branch.
3. Make your changes.
4. Commit your changes.
5. Push to your forked repository.
6. Create a pull request.

Please follow the code style and testing guidelines.