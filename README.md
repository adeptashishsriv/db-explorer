# DB Explorer
## Database Query Tool & Explorer

**Version:** 2.4.1  
**Release Date:** March 31, 2026  
**Built by:** Ashish Srivastava  
**Copyright:** © 2026 Astro Adept AI Labs

---

## Table of Contents

1. [Overview](#overview)
2. [Features](#features)
3. [System Requirements](#system-requirements)
4. [Installation](#installation)
5. [Quick Start](#quick-start)
6. [User Interface](#user-interface)
7. [Configuration](#configuration)
8. [Database Support](#database-support)
9. [Troubleshooting](#troubleshooting)
10. [Release Notes](#release-notes)
11. [License](#license)
12. [Support](#support)

---

## Overview

DB Explorer is a powerful, user-friendly database query tool and explorer designed for developers, database administrators, and data analysts. It provides an intuitive graphical interface for connecting to various database systems, executing SQL queries, and exploring database schemas.

**Key capabilities include:**
- Multi-database support (PostgreSQL, MySQL, SQL Server, Oracle, SQLite, etc.)
- Real-time query execution with syntax highlighting
- Interactive schema browser with expandable tree navigation
- Table data viewer and editor
- Query result export capabilities
- Connection management and health monitoring
- Memory management and performance monitoring

---

## Features

### Core Features
- Multi-tab query interface with syntax highlighting
- Real-time SQL execution and result display
- Interactive database schema explorer
- Table data viewer with pagination
- Query result export (CSV, INSERT, UPDATE, DDL)
- Connection health monitoring and auto-reconnection
- Memory usage monitoring with manual garbage collection

### User Experience
- Modern, responsive GUI with multiple themes
- Color-coded status messages and visual feedback
- Confirmation dialogs for destructive operations
- Keyboard shortcuts and intuitive navigation
- Context-sensitive help and tooltips

### Database Features
- Support for major database systems
- Live connection monitoring
- Schema exploration (tables, views, procedures, etc.)
- Column metadata and data type information
- Foreign key relationships
- Index and constraint information

### Performance & Reliability
- Lazy loading for large result sets
- Configurable memory limits and fetch sizes
- Automatic connection pooling and reconnection
- Background query execution
- Memory leak prevention and cleanup

---

## System Requirements

### Minimum Requirements
- **Operating System:** Windows 10+, macOS 10.14+, Linux (Ubuntu 18.04+)
- **Java Runtime:** Java 17 or higher
- **Memory:** 512 MB RAM (1 GB recommended)
- **Disk Space:** 100 MB free space
- **Display:** 1024x768 resolution or higher

### Recommended Specifications
- **Operating System:** Windows 11, macOS 12+, Linux (Ubuntu 20.04+)
- **Java Runtime:** Java 17 LTS
- **Memory:** 2 GB RAM or more
- **Disk Space:** 500 MB free space
- **Display:** 1920x1080 resolution or higher

### Supported Databases
- PostgreSQL 9.0+
- MySQL 5.5+
- MariaDB 10.0+
- Microsoft SQL Server 2012+
- Oracle Database 11g+
- SQLite 3.0+
- Amazon DynamoDB
- Generic JDBC-compatible databases

---

## Installation

### Option 1: Pre-Built Distribution (Recommended)

1. Download the latest release from the project repository
2. Extract the ZIP file to your desired location
3. **For JVM-bundled version:**
   - Run: `db-explorer.exe` (Windows) or `./db-explorer` (Linux/Mac)
4. **For JVM-independent version:**
   - Ensure Java 17+ is installed
   - Run: `java -jar db-explorer.jar`

### Option 2: Build from Source

**Prerequisites:**
- Java Development Kit (JDK) 17 or higher
- Apache Maven 3.6 or higher
- Git (optional, for cloning)

**Steps:**
1. Clone or download the source code
2. Open terminal/command prompt in the project directory
3. Run: `mvn clean package`
4. Find the JAR file in: `target/db-explorer-2.4.1.jar`
5. Run: `java -jar target/db-explorer-2.4.1.jar`

### Option 3: Development Setup

For developers who want to modify the source code:

1. Clone the repository: `git clone <repository-url>`
2. Import as Maven project in your IDE (IntelliJ IDEA, Eclipse, etc.)
3. Ensure JDK 17+ is configured
4. Run the main class: `com.dbexplorer.ui.MainFrame`

---

## Quick Start

### First Time Setup

1. Launch DB Explorer
2. The application opens with an empty connections panel
3. Click the "+" button in the toolbar to add a new database connection
4. Fill in connection details:
   - **Connection Name:** A friendly name for your database
   - **Database Type:** Select from dropdown (PostgreSQL, MySQL, etc.)
   - **Host:** Database server hostname or IP
   - **Port:** Database port (default ports are pre-filled)
   - **Database Name:** The specific database to connect to
   - **Username/Password:** Database credentials

5. Click "Test Connection" to verify settings
6. Click "Save" to store the connection
7. Double-click the connection in the tree to connect

### Executing Your First Query

1. Once connected, click the "New Tab" button in the toolbar
2. A new query tab opens with a SQL editor
3. Type your SQL query in the editor
4. Click the green "Run Query" button or press `Ctrl+Enter`
5. Results appear in the table below the editor
6. Use the toolbar buttons to export results if needed

### Exploring Database Schema

1. In the left panel (Connections), expand your database connection
2. Navigate through the schema tree:
   - Expand schema names
   - Browse tables, views, procedures, etc.
   - Right-click items for context menu options
3. Double-click tables to view data
4. Expand table nodes to see columns and their data types

---

## User Interface

### Main Window Layout

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ [New Connection] [Run] [Cancel] [Explain Plan] [Health]                     │
├─────────────────────────────────────────────────────────────────────────────┤
│ ┌─────────────┐ ┌─────────────────────────────────────┐ ┌─────────────────┐ │
│ │ Connections │ │         Query Editor                │ │   Connection    │ │
│ │   Tree      │ │                                     │ │     Health      │ │
│ │             │ ├─────────────────────────────────────┤ │                 │ │
│ │             │ │         Results Table               │ │                 │ │
│ │             │ │                                     │ │                 │ │
│ └─────────────┘ └─────────────────────────────────────┘ └─────────────────┘ │
├─────────────────────────────────────────────────────────────────────────────┤
│ Status: Connected to PostgreSQL_Prod | 🗑 Heap: 256 MB / 1024 MB (25%)       │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Toolbar Buttons
- **New Tab:** Create a new query tab
- **Run Query:** Execute the current SQL query (green button)
- **Cancel Query:** Stop a running query (red button)
- **Disconnect:** Disconnect from current database
- **Clear Console:** Clear the log panel
- **Health Dashboard:** Open database monitoring panel
- **About:** Show application information

### Status Bar Elements
- Connection status and database name
- Heap memory usage with progress bar
- Garbage collection button (🗑)
- Color-coded status messages (red/amber/green)

### Keyboard Shortcuts
- `Ctrl+N`: New query tab
- `Ctrl+Enter`: Run query
- `Ctrl+S`: Save current query
- `F5`: Refresh schema tree
- `Ctrl+F`: Find in results
- `Ctrl+E`: Export results

---

## Configuration

### Application Configuration

The main configuration file is located at:
- **Windows:** `%APPDATA%\DBExplorer\app.properties`
- **macOS:** `~/Library/Application Support/DBExplorer/app.properties`
- **Linux:** `~/.config/DBExplorer/app.properties`

**Key configuration options:**

```properties
# Query execution settings
query.max.rows=10000          # Maximum rows to load per query
query.fetch.size=500          # Rows fetched per page

# Export settings
export.fetch.size=500         # Rows fetched during export

# UI settings
ui.theme=default              # Application theme
ui.font.size=12               # Base font size

# Connection settings
connection.timeout=30         # Connection timeout in seconds
connection.pool.size=5        # Connection pool size
```

### Database Connections

Connections are stored securely and can be managed through the UI:

1. Click the "+" button in the toolbar
2. Select database type from dropdown
3. Enter connection details
4. Test connection before saving
5. Use the context menu for connection management

### Supported Connection Types
- Direct JDBC connections
- SSH tunnel connections (planned)
- SSL/TLS encrypted connections
- Connection pooling configurations

---

## Database Support

### Fully Supported Databases

#### PostgreSQL
- Full schema exploration
- Table/view/procedure browsing
- Live statistics via pg_stat_* views
- JSON/JSONB data type support
- Array and composite type handling

#### MySQL/MariaDB
- Complete schema information
- Performance schema integration
- Stored procedure and function support
- MySQL-specific data types

#### Microsoft SQL Server
- Comprehensive schema metadata
- SQL Server Management Objects integration
- Dynamic management views support
- Linked server browsing

#### Oracle Database
- Advanced schema exploration
- Oracle-specific object types
- Performance monitoring via v$ views
- PL/SQL object support

#### SQLite
- File-based database support
- PRAGMA statement integration
- Lightweight schema browsing
- WAL mode support

#### Amazon DynamoDB
- NoSQL table browsing
- Item count and size information
- Key schema display
- Basic query capabilities

#### Generic JDBC
- Any JDBC-compatible database
- Basic schema information
- Standard SQL support
- Connection health monitoring

---

## Troubleshooting

### Common Issues and Solutions

#### Application Won't Start
- Ensure Java 17+ is installed: `java -version`
- Check available memory: `java -Xmx1024m -jar db-explorer.jar`
- Verify JAR file integrity
- Check console output for error messages

#### Connection Failures
- Verify database server is running
- Check network connectivity and firewall settings
- Confirm username/password credentials
- Test connection using database client tools
- Check JDBC driver compatibility

#### Out of Memory Errors
- Increase heap size: `java -Xmx2048m -jar db-explorer.jar`
- Reduce `query.max.rows` in app.properties
- Use pagination for large result sets
- Monitor memory usage in status bar

#### Query Performance Issues
- Add appropriate indexes to database tables
- Use EXPLAIN PLAN to analyze query execution
- Limit result sets with LIMIT/OFFSET clauses
- Consider query optimization techniques

#### Schema Browsing Issues
- Ensure user has sufficient privileges
- Check database permissions for metadata access
- Refresh schema tree (F5)
- Verify connection stability

#### Export Problems
- Check file system permissions for export location
- Ensure sufficient disk space
- Verify export format compatibility
- Check for special characters in data

### Logging and Debugging

Enable debug logging by adding to JVM arguments:
```
-Dlogging.level.com.dbexplorer=DEBUG
```

Log files are located in:
- **Windows:** `%APPDATA%\DBExplorer\logs\`
- **macOS:** `~/Library/Logs/DBExplorer/`
- **Linux:** `~/.cache/DBExplorer/logs/`

---

## Release Notes

For detailed release notes, see [RELEASE_NOTES.md](docs/RELEASE_NOTES.md) in the docs/ folder.

### Recent Versions

#### Version 2.4.1 (March 31, 2026)
- Connection safety improvements
- Double-click protection for disconnections
- Enhanced user confirmation dialogs

#### Version 2.4.0 (March 31, 2026)
- Enhanced UX for Disconnect and Run Query buttons
- Color-coded status message system
- Improved validation and feedback

#### Version 2.3.0 (March 31, 2026)
- Garbage collection button for memory management
- Manual memory control features

#### Version 2.2.0 (March 31, 2026)
- Memory leak fixes
- Configuration externalization
- Performance improvements

#### Version 2.1.0 (Previous)
- Database Health Dashboard
- Theme fixes and UI improvements

---

## License

DB Explorer is proprietary software developed by Astro Adept AI Labs.

Copyright (c) 2026 Astro Adept AI Labs. All rights reserved.

This software is provided "as is" without warranty of any kind. The authors and copyright holders disclaim all warranties, express or implied, including but not limited to the implied warranties of merchantability and fitness for a particular purpose.

Permission is granted to use this software for personal, educational, and commercial purposes, subject to the following conditions:

1. This copyright notice must be included in all copies or substantial portions of the software.

2. The software may not be redistributed or sold without explicit written permission from Astro Adept AI Labs.

3. Any modifications to the source code must be clearly marked and documented.

For licensing inquiries, please contact:   
Astro Adept AI Labs  
Email: adeptashish@gmail.com

---

## Support

### Support Options

#### Documentation
- User Manual: [docs/USER_HANDBOOK.md](docs/USER_HANDBOOK.md)
- API Documentation: [docs/DEVELOPERS_GUIDE.md](docs/DEVELOPERS_GUIDE.md)
- Troubleshooting Guide: [docs/EXECUTION_AND_DEPLOYMENT_GUIDE.md](docs/EXECUTION_AND_DEPLOYMENT_GUIDE.md)
- Feature Documentation: [docs/](docs/) directory

#### Community Support
- GitHub Issues: Report bugs and request features
- Discussion Forums: Community-driven support
- Stack Overflow: Tag questions with 'db-explorer'

#### Professional Support
- Email: adeptashish@gmail.com
- Priority response for licensed users
- Custom development and integration services
- Training and consulting services

### Reporting Issues

When reporting bugs, please include:

1. DB Explorer version and build date
2. Operating system and Java version
3. Database type and version
4. Steps to reproduce the issue
5. Expected vs. actual behavior
6. Log files and error messages
7. Screenshot if applicable

### Feature Requests

Feature requests are welcome! Please include:

1. Use case description
2. Expected behavior
3. Database systems affected
4. Priority level (nice-to-have vs. critical)
5. Any mockups or examples

---

## Thank You for Using DB Explorer!

DB Explorer is designed to make database development and administration more efficient and enjoyable. We appreciate your feedback and contributions to making it even better.

For the latest updates and releases, visit our project repository.

**Happy querying!**

---

*Built by Astro Adept AI Labs | © 2026*
