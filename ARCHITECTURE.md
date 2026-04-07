# DVCS Project Architecture

## Folder Structure

```
dvcs-root/
├── client/                          # Main JavaFX application
│   ├── src/main/java/com/dvcs/client/
│   │   ├── MainApp.java            # Application entry point & main window
│   │   │
│   │   ├── auth/                   # Authentication & user management
│   │   │   ├── AuthExampleMain.java    # Auth example/demo
│   │   │   ├── model/              # User data model (User.java)
│   │   │   ├── repo/               # UserRepository (MongoDB access)
│   │   │   ├── db/                 # MongoConnection singleton
│   │   │   ├── service/            # UserService (business logic)
│   │   │   └── ui/                 # Login/Signup UI controllers
│   │   │
│   │   ├── controller/             # Main UI navigation controllers
│   │   │   ├── LandingController.java   # Landing page
│   │   │   └── LoginSignupController.java # Auth pages
│   │   │
│   │   ├── dashboard/              # Dashboard feature & workspace management
│   │   │   ├── analytics/          # Analytics charts
│   │   │   ├── content/            # Main content area
│   │   │   ├── data/               # DAOs (database access)
│   │   │   ├── navbar/             # Navigation bar
│   │   │   ├── notification/       # Notifications panel
│   │   │   ├── profile/            # User profile page
│   │   │   ├── search/             # Search functionality
│   │   │   └── workspace/          # Workspace cards & display
│   │   │
│   │   └── workspacepage/          # Workspace editor & file management
│   │       ├── controller/         # Workspace UI controllers
│   │       ├── dao/                # DAOs (database layer)
│   │       ├── model/              # Data models (Workspace, File, Folder)
│   │       ├── service/            # Business logic services
│   │       └── utils/              # Helper utilities
│   │
│   ├── src/main/resources/
│   │   ├── fxml/                   # JavaFX UI layouts (XML format)
│   │   ├── css/                    # Stylesheets for UI theming
│   │   └── images/                 # Application images & icons
│   │
│   ├── src/test/java               # Unit tests (currently empty)
│   │
│   ├── lib/                        # External dependencies (JAR files)
│   │   ├── mongodb-driver-sync-5.3.1.jar
│   │   ├── mongodb-driver-core-5.3.1.jar
│   │   ├── bson-5.3.1.jar
│   │   ├── jbcrypt-0.4.jar         # Password encryption
│   │   └── slf4j-*.jar             # Logging framework
│   │
│   ├── out/                        # Compiled .class files (from javac)
│   └── pom.xml                     # Maven config (removed)
│
├── table.sql                       # Original SQL schema (reference only)
├── schema.txt                      # Current MongoDB schema (active)
└── ARCHITECTURE.md                 # This file

```

---

## Architecture Layers

### **Presentation Layer (UI)**
- **FXML Files** - UI layout definitions (landing.fxml, login_signup.fxml, etc.)
- **Controllers** - Handle UI events (LandingController, LoginSignupController, etc.)
- **CSS** - Visual styling (dashboard.css, styles.css)

### **Business Logic Layer**
- **Services** - Core operations (UserService, WorkspaceService, FileService, etc.)
- **Models** - Data objects (User, Workspace, File, Folder, etc.)

### **Data Access Layer (DAO/Repository)**
- **Repositories** - User data access (UserRepository)
- **DAOs** - Workspace/File/Folder data access (WorkspaceDAO, FileDAO, FolderDAO, etc.)

### **Database Layer**
- **MongoConnection** - Singleton connection manager
- **MongoDB** - NoSQL database (7 collections: users, workspaces, folders, files, snapshots, commits, collaboration_requests)

---

## Technology Stack

| Component | Technology |
|-----------|------------|
| UI Framework | JavaFX (with FXML) |
| Database | MongoDB (NoSQL) |
| Language | Java 21 |
| Password Hashing | BCrypt |
| Logging | SLF4J |
| Build Tool | Manual javac (Maven removed) |

---

## Key Features

- ✅ User Authentication & Authorization
- ✅ Workspace Management
- ✅ File Versioning & Snapshots
- ✅ Collaboration Requests
- ✅ Dashboard & Analytics
- ✅ File Locking Mechanism
