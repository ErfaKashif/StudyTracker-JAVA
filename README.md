# StudyTracker-JAVA
Java Swing-based study management platform with AI-powered flashcard generation, note organization, task tracking, JSON persistence, and Ollama integration.

## Overview

StudyTrack AI is a desktop study management platform built using Java Swing. The application helps students organize subjects, manage notes, track tasks, and generate AI-powered flashcards using local Large Language Models through Ollama.

The project extends the original StudyTracker concept by introducing a graphical user interface, persistent storage, and artificial intelligence features to improve the learning experience.

---

## Features

### User Authentication

* User registration
* Secure login system
* SHA-256 password hashing

### Subject Management

* Create and organize subjects
* Track study categories
* Subject-based filtering

### Notes Management

* Create and edit study notes
* Store notes using HTML formatting
* Organize notes by subject

### AI Flashcard Generation

* Generate flashcards directly from notes
* Automatic question-answer creation
* Multiple-choice support
* Integration with Ollama

### Flashcard Deck Management

* Create and manage decks
* Filter decks by subject
* Review generated flashcards

### Study Mode

* Interactive flashcard review sessions
* Card shuffling
* Progress tracking

### Task Management

* Create study tasks
* Set deadlines
* Mark tasks as completed
* Track study goals

### Persistent Storage

* JSON-based data storage
* Automatic loading and saving
* Lightweight local database alternative

---

## Technologies Used

* Java
* Java Swing
* JSON
* Ollama
* SHA-256 Hashing
* File Handling
* Multithreading (CompletableFuture)
* Object-Oriented Programming

---

## System Architecture

### Models

* User
* Subject
* Note
* FlashcardDeck
* FlashcardCard
* TaskModel

### Utility Classes

* JsonUtil
* HashUtil
* HtmlUtil

### Storage Layer

* Storage Manager
* JSON Persistence

### User Interface

* Subjects View
* Notes View
* Flashcards View
* Tasks View
* Study Dialog

### AI Layer

* Note Validation
* Flashcard Generation
* Ollama Integration

---

## Concepts Demonstrated

* Object-Oriented Design
* GUI Development
* JSON Serialization
* Reflection
* Asynchronous Programming
* AI Integration
* Local Data Persistence
* MVC-Inspired Architecture

---

## Future Improvements

* Database Integration (MySQL/PostgreSQL)
* Cloud Backup
* Spaced Repetition Algorithms
* Study Analytics Dashboard
* Collaborative Study Rooms
* Mobile Application Version
* Multi-user Synchronization

---

## Project Goal

The goal of StudyTrack AI is to combine traditional study tools with modern AI assistance to create a more efficient and personalized learning environment for students.
