# 📱 WanderPlan Android App - Technical Documentation

## 1. Specific Technologies/Tools Used

### **Development Environment**
- **Android Studio:** Latest Version (Flamingo/Arctic Fox compatible)
- **Programming Language:** **Java** (100% Java implementation)
- **Android SDK:**
  - **Min SDK:** API 24 (Android 7.0 Nougat)
  - **Target SDK:** API 34 (Android 14)
  - **Compile SDK:** API 35
- **Java Version:** Java 11 (sourceCompatibility & targetCompatibility)
- **Build System:** Gradle with Kotlin DSL (.gradle.kts)

### **Core Android Libraries**
```gradle
// Core Android Components
implementation 'androidx.appcompat:appcompat:1.7.1'
implementation 'com.google.android.material:material:1.12.0'
implementation 'androidx.constraintlayout:constraintlayout:2.2.1'
implementation 'androidx.activity:activity:1.10.1'

// Navigation
implementation 'androidx.navigation:navigation-fragment:2.9.0'
implementation 'androidx.navigation:navigation-ui:2.9.0'

// Room Database
implementation 'androidx.room:room-runtime:2.7.2'
annotationProcessor 'androidx.room:room-compiler:2.7.2'

// Lifecycle (ViewModel, LiveData)
implementation 'androidx.lifecycle:lifecycle-viewmodel:2.9.1'
implementation 'androidx.lifecycle:lifecycle-livedata:2.9.1'

// UI Components
implementation 'androidx.recyclerview:recyclerview:1.4.0'
implementation 'androidx.cardview:cardview:1.0.0'
implementation 'androidx.viewpager2:viewpager2:1.1.0'
implementation 'androidx.fragment:fragment:1.8.8'
```

### **Third-Party Libraries**
```gradle
// Firebase (BOM version 33.15.0)
implementation platform('com.google.firebase:firebase-bom:33.15.0')
implementation 'com.google.firebase:firebase-auth'
implementation 'com.google.firebase:firebase-firestore'
implementation 'com.google.firebase:firebase-storage'
implementation 'com.google.firebase:firebase-analytics'

// Image Loading
implementation 'com.github.bumptech.glide:glide:4.16.0'

// Animations
implementation 'com.airbnb.android:lottie:6.6.7'

// Charts and Analytics
implementation 'com.github.PhilJay:MPAndroidChart:v3.1.0'

// JSON Serialization
implementation 'com.google.code.gson:gson:2.13.1'
```

### **What We DON'T Use (Clarification)**
- ❌ **Retrofit/Volley:** No network libraries (Firebase SDK handles networking)
- ❌ **Jetpack Compose:** Traditional XML layouts only
- ❌ **DataStore:** SharedPreferences for data persistence
- ❌ **Kotlin Coroutines/Flow:** Java with LiveData and ExecutorService
- ❌ **Picasso:** Glide for image loading

---

## 2. Modules/Features Built

### **📱 Core Features Implementation**

#### **2.1 Trip Creation/Edit/Delete**
**Data Model:**
```java
@Entity(tableName = "trips")
public class Trip {
    @PrimaryKey(autoGenerate = true)
    private int id;
    private String firebaseId;      // Firebase sync ID
    private String title;           // Trip name
    private String destination;     // Location
    private long startDate;         // Timestamp
    private long endDate;           // Timestamp
    private String mapImageUrl;     // Static map
    private double latitude;        // GPS coordinates
    private double longitude;
    private long createdAt;
    private long updatedAt;
    private boolean synced;         // Sync status
}
```

**Implementation:**
- **Create:** `AddTripActivity` with DatePickerDialog validation
- **Edit:** In-place editing with pre-populated forms
- **Delete:** Confirmation dialogs with cascade deletion (removes all activities)
- **Validation:** No past dates, end date after start date

#### **2.2 Expense Tracker**
**Categories:**
```java
public enum Category {
    FOOD("Food", "🍔"),
    TRANSPORT("Transport", "🚗"),
    HOTEL("Hotel", "🏨"),
    ACTIVITIES("Activities", "🎟️"),
    SHOPPING("Shopping", "🛍️"),
    OTHER("Other", "📝");
}
```

**Features:**
- **Currency:** Malaysian Ringgit (RM) formatting
- **Storage:** SharedPreferences with JSON serialization
- **Totals:** Automatic calculation with budget comparison
- **Analytics:** Pie charts with MPAndroidChart library

#### **2.3 Photo Attachment**
**Storage Strategy:**
```java
// Local Storage
private String saveBitmapToInternalStorage(Bitmap bitmap)

// Firebase Storage
private void uploadBitmapToFirebase(Bitmap bitmap, ImageUploadCallback callback)
```

**Implementation:**
- **Selection:** `Intent.ACTION_PICK` from gallery
- **Preview:** Glide library with automatic caching
- **Compression:** Automatic bitmap compression before storage
- **Dual Storage:** Local cache + Firebase Storage URLs

#### **2.4 Theme Switching**
**Implementation:**
```java
public class ThemeManager {
    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK = "dark";
    public static final String THEME_SYSTEM = "system";
    
    // 8 Color variants
    public static final String COLOR_PASTEL_PINK = "pastel_pink";
    public static final String COLOR_FOREST_GREEN = "forest_green";
    // ... more colors
}
```

**Storage:** SharedPreferences for theme persistence
**Application:** Immediate theme changes without restart

#### **2.5 Offline Mode**
**Architecture:**
- **Primary:** Room SQLite database
- **Secondary:** Firebase Firestore synchronization
- **Strategy:** Offline-first with background sync
- **Conflict Resolution:** User choice dialogs for data conflicts

#### **2.6 Sharing Feature**
**Content Shared:**
```java
private String buildShareContent() {
    // Trip overview with emojis
    // Complete activity timeline
    // Budget breakdown with expenses
    // Professional formatting
    // App branding
}
```

**Implementation:** `Intent.ACTION_SEND` for universal app compatibility

---

## 4.2 Module Implementation Details

### **4.2.1 Trip Planning Module**
Users can add, edit, and delete trips with comprehensive validation and data management.

**Core Implementation:**
```java
// Trip validation in AddTripActivity
private void showStartDatePicker() {
    DatePickerDialog datePickerDialog = new DatePickerDialog(this,
        (view, year, month, dayOfMonth) -> {
            startCalendar.set(year, month, dayOfMonth);
            // Auto-adjust end date if start date is after end date
            if (startCalendar.after(endCalendar)) {
                endCalendar.setTime(startCalendar.getTime());
                endCalendar.add(Calendar.DAY_OF_MONTH, 1);
            }
            updateDateDisplays();
        }, startCalendar.get(Calendar.YEAR), startCalendar.get(Calendar.MONTH), 
           startCalendar.get(Calendar.DAY_OF_MONTH));
    
    // Prevent past dates
    datePickerDialog.getDatePicker().setMinDate(System.currentTimeMillis());
    datePickerDialog.show();
}

// Trip constructor with validation
@androidx.room.Ignore
public Trip(String title, String destination, long startDate, long endDate) {
    if (title == null || title.trim().isEmpty()) {
        throw new IllegalArgumentException("Trip title cannot be null or empty");
    }
    if (endDate < startDate) {
        throw new IllegalArgumentException("End date must be after start date");
    }
    // Additional validation logic...
}
```

**Data Storage:** Room entities with Firebase sync support, GPS coordinates stored, and automatic timestamp management.

### **4.2.2 Expense Tracker**
Comprehensive expense management with categorization, GSON serialization, and visual analytics.

**Core Implementation:**
```java
// Expense model with categories
public class Expense {
    public enum Category {
        FOOD("Food", "🍔"), TRANSPORT("Transport", "🚗"),
        HOTEL("Hotel", "🏨"), ACTIVITIES("Activities", "🎟️"),
        SHOPPING("Shopping", "🛍️"), OTHER("Other", "📝");
    }
    
    public String getFormattedAmount() {
        return String.format(Locale.getDefault(), "RM%.2f", amount);
    }
}

// Local storage with GSON serialization
public void saveBudgetDataLocally(Map<Integer, Double> tripBudgets, 
                                  Map<Integer, List<Expense>> tripExpenses) {
    SharedPreferences.Editor editor = sharedPreferences.edit();
    String budgetsJson = gson.toJson(tripBudgets);
    String expensesJson = gson.toJson(tripExpenses);
    editor.putString("trip_budgets", budgetsJson);
    editor.putString("trip_expenses", expensesJson);
    editor.apply();
}

// Chart integration with MPAndroidChart
private void updateChartData() {
    PieChart pieChart = findViewById(R.id.expense_chart);
    // Populate chart with category-wise expenses
}
```

**Features:** Real-time budget tracking, category-wise analytics, Firebase sync for logged users, and offline-first data persistence.

### **4.2.3 Photo Attachment**
Sophisticated image handling with compression, dual storage, and Glide integration.

**Core Implementation:**
```java
// Image selection and processing
private void selectImage() {
    Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
    imagePickerLauncher.launch(intent);
}

// Glide-powered image compression and upload
private void handleImageAndSave() {
    Glide.with(this).asBitmap().load(selectedImageUri)
        .into(new CustomTarget<Bitmap>() {
            @Override
            public void onResourceReady(@NonNull Bitmap resource, 
                                       @Nullable Transition<? super Bitmap> transition) {
                if (userManager.isLoggedIn()) {
                    uploadBitmapToFirebase(resource, url -> saveData(url));
                } else {
                    String localPath = saveBitmapToInternalStorage(resource);
                    saveData(localPath);
                }
            }
        });
}

// Firebase Storage upload with compression
private void uploadBitmapToFirebase(Bitmap bitmap, ImageUploadCallback callback) {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos); // 80% quality
    byte[] data = baos.toByteArray();
    
    String imageName = "activity_" + System.currentTimeMillis() + ".jpg";
    StorageReference imageRef = FirebaseStorage.getInstance().getReference()
        .child("activity_images/" + imageName);

    imageRef.putBytes(data).addOnSuccessListener(taskSnapshot -> 
            imageRef.getDownloadUrl().addOnSuccessListener(uri -> 
                callback.onUrlReady(uri.toString())));
}
```

**Storage Strategy:** Local internal storage for offline users, Firebase Storage for cloud users, automatic compression to prevent memory issues.

### **4.2.4 Theme Switching & Personalization**
Real-time theme changes with comprehensive color variants and persistent preferences.

**Core Implementation:**
```java
// ThemeManager with 8 color variants
public class ThemeManager {
    public static final String THEME_PASTEL_PINK = "PastelPink";
    public static final String THEME_FOREST_GREEN = "ForestGreen";
    public static final String THEME_SUNSET_ORANGE = "SunsetOrange";
    // ... 5 more color themes
    
    public static void applyColorTheme(Context context) {
        String colorTheme = getColorTheme(context);
        switch (colorTheme) {
            case THEME_FOREST_GREEN:
                context.setTheme(R.style.Theme_WanderPlan_ForestGreen);
                break;
            case THEME_SUNSET_ORANGE:
                context.setTheme(R.style.Theme_WanderPlan_SunsetOrange);
                break;
            // ... more themes
        }
    }
    
    public static void setColorTheme(Context context, String colorTheme) {
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS_NAME, 
                                                     Context.MODE_PRIVATE).edit();
        editor.putString(KEY_COLOR_THEME, colorTheme);
        editor.apply();
    }
}

// BaseActivity applies theme on creation
public abstract class BaseActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ThemeManager.applyTheme(this); // Applied before super.onCreate()
        super.onCreate(savedInstanceState);
    }
}
```

**Features:** Instant theme application without restart, SharedPreferences persistence, Light/Dark/System mode support, and 8 distinct color variants.

### **4.2.5 Offline Mode & Sync Logic**
Sophisticated offline-first architecture with intelligent conflict resolution.

**Core Implementation:**
```java
// Dual-source data loading
private void loadInitialTripData() {
    UserManager userManager = UserManager.getInstance(this);
    if (userManager.isLoggedIn()) {
        loadTripFromFirebaseOnlyNuclear(); // Cloud-first for logged users
    } else {
        loadTripDataLocalOnly(); // Local-only for guest users
    }
}

// Background sync with conflict detection
public void syncAllDataToFirebase(OnSyncCompleteListener listener) {
    executor.execute(() -> {
        List<Trip> allTrips = tripRepository.getAllTripsSync();
        AtomicInteger completedTrips = new AtomicInteger(0);
        
        for (Trip trip : allTrips) {
            syncTripAsJson(userEmail, trip, syncedBudgetEntries, 
                new OnTripSyncListener() {
                    @Override
                    public void onSuccess() {
                        int completed = completedTrips.incrementAndGet();
                        if (completed == allTrips.size()) {
                            listener.onSuccess(completed, totalActivities);
                        }
                    }
                });
        }
    });
}

// Conflict resolution with user choice
private void showSyncPromptDialog() {
    new AlertDialog.Builder(this)
        .setTitle("Data Sync Options")
        .setMessage("Local and cloud data differ. What would you like to do?")
        .setPositiveButton("Use Cloud Data", (dialog, which) -> useCloudData())
        .setNegativeButton("Keep Local Data", (dialog, which) -> keepLocalData())
        .setNeutralButton("Sync All", (dialog, which) -> syncAllData())
        .show();
}
```

**Strategy:** Room as primary storage, Firebase as secondary cloud backend, smart conflict resolution, and user-controlled data management.

### **4.2.6 Sharing Module**
Professional trip sharing with comprehensive formatting and universal app compatibility.

**Core Implementation:**
```java
// Main sharing method
private void shareTrip() {
    if (currentTrip == null) return;
    
    Toast.makeText(this, "Preparing trip data for sharing...", Toast.LENGTH_SHORT).show();
    
    new Thread(() -> {
        String shareContent = buildShareContent();
        runOnUiThread(() -> createAndStartShareIntent(shareContent));
    }).start();
}

// Formatted content generation
private String buildShareContent() {
    StringBuilder content = new StringBuilder();
    
    // Trip header with emojis
    content.append("✈️ ").append(currentTrip.getTitle()).append("\n");
    content.append("📍 ").append(currentTrip.getDestination()).append("\n");
    content.append("📅 ").append(currentTrip.getDateRange()).append("\n");
    content.append("⏰ ").append(currentTrip.getDurationDays()).append(" days\n\n");
    
    // Activities section
    List<TripActivity> activities = getCurrentActivities();
    if (activities != null && !activities.isEmpty()) {
        content.append("🎯 ACTIVITIES:\n");
        content.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        
        Map<Integer, List<TripActivity>> activitiesByDay = groupActivitiesByDay(activities);
        for (Map.Entry<Integer, List<TripActivity>> entry : activitiesByDay.entrySet()) {
            content.append("\n📆 Day ").append(entry.getKey()).append(":\n");
            content.append("─────────────────────────────────────────────\n");
            
            for (TripActivity activity : entry.getValue()) {
                content.append("• ").append(activity.getTitle());
                if (activity.getTimeString() != null) {
                    content.append(" (").append(activity.getTimeString()).append(")");
                }
                content.append("\n");
                
                if (activity.getDescription() != null) {
                    content.append("  ").append(activity.getDescription()).append("\n");
                }
                if (activity.getLocation() != null) {
                    content.append("  📍 ").append(activity.getLocation()).append("\n");
                }
                content.append("\n");
            }
        }
    }
    
    // Budget section with expense breakdown
    String budgetInfo = getBudgetInfo();
    if (budgetInfo != null) content.append(budgetInfo);
    
    // Professional footer
    content.append("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
    content.append("📱 Shared from WanderPlan Travel App\n");
    content.append("🌟 Plan your next adventure with us!");
    
    return content.toString();
}

// Intent creation for universal sharing
private void createAndStartShareIntent(String content) {
    Intent shareIntent = new Intent(Intent.ACTION_SEND);
    shareIntent.setType("text/plain");
    shareIntent.putExtra(Intent.EXTRA_TEXT, content);
    shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Trip: " + currentTrip.getTitle());
    
    Intent chooserIntent = Intent.createChooser(shareIntent, "Share Trip via");
    chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    startActivity(chooserIntent);
}
```

**Features:** Complete trip summaries with timeline, budget breakdown with category analysis, emoji-enhanced formatting, and compatibility with WhatsApp, Email, and all sharing apps.

---

## 3. Code Practices

### **Architecture Pattern: MVVM**
```java
// View Layer: Activities & Fragments handle UI and user interactions
public class TripDetailActivity extends BaseActivity {
    private TripsViewModel viewModel;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(TripsViewModel.class);
        observeData();
    }
    
    private void observeData() {
        viewModel.getTripById(tripId).observe(this, trip -> {
            if (trip != null) displayTripInfo(trip);
        });
    }
}

// ViewModel Layer: Business logic and state management
public class TripsViewModel extends ViewModel {
    private TripRepository repository;
    
    public LiveData<List<Trip>> getAllTrips() {
        return repository.getAllTrips();
    }
    
    public void insertTrip(Trip trip, TripRepository.OnTripOperationListener listener) {
        repository.insertTrip(trip, listener);
    }
}

// Repository Layer: Data source abstraction
public class TripRepository {
    private TripDao tripDao;
    private FirebaseFirestore firestore;
    
    public LiveData<List<Trip>> getAllTrips() {
        return tripDao.getAllTrips(); // Room LiveData
    }
    
    public void insertTrip(Trip trip, OnTripOperationListener listener) {
        // Save to Room first, then sync to Firebase
        executor.execute(() -> {
            long tripId = tripDao.insertTrip(trip);
            if (userManager.isLoggedIn()) {
                syncTripToFirebase(trip, listener);
            }
        });
    }
}
```

### **Data Flow & Architecture**
- **Offline-First:** Room SQLite as primary database
- **LiveData Integration:** Reactive UI updates
- **ExecutorService:** Background operations
- **Firebase Sync:** Secondary cloud storage
- **GSON Serialization:** SharedPreferences JSON storage

### **Error Handling**
```java
// Comprehensive try-catch with user feedback
private void saveTrip() {
    try {
        Trip trip = new Trip(title, destination, startDate, endDate);
        viewModel.insertTrip(trip, new TripRepository.OnTripOperationListener() {
            @Override
            public void onSuccess(int tripId) {
                runOnUiThread(() -> showSuccessDialog());
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(this, "Error: " + error, 
                                                  Toast.LENGTH_LONG).show());
            }
        });
    } catch (IllegalArgumentException e) {
        Toast.makeText(this, "Validation error: " + e.getMessage(), 
                      Toast.LENGTH_LONG).show();
    }
}
```

---

## 4. Testing

### **4.1 Testing Framework**
- **JUnit 4.13.2:** Primary unit testing framework
- **Espresso 3.6.1:** UI testing (basic setup)
- **Manual Testing:** Comprehensive feature validation

### **4.2 Unit Testing Implementation**
```java
@Test
public void testTripSharingContent() {
    // Create sample trip and activities
    Trip trip = new Trip("Paris Adventure", "Paris, France", ...);
    
    // Test share content generation
    String shareContent = buildTestShareContent(trip, activities, expenses, 500.0);
    
    // Assertions
    assertTrue("Share content should contain trip title", 
               shareContent.contains("Paris Adventure"));
    assertTrue("Share content should contain activities section", 
               shareContent.contains("ACTIVITIES"));
}
```

### **Testing Tools Used**
- **JUnit 4.13.2:** Unit testing framework
- **Espresso 3.6.1:** UI testing (basic setup)
- **Manual Testing:** Primary validation method

### **Core Feature Validation**
- **Expense Calculation:** Unit tests for budget math
- **Data Persistence:** Manual testing of offline/online scenarios
- **Sync Logic:** Manual validation of Firebase synchronization
- **User Flows:** End-to-end manual testing

---

## 5. Special Implementation & Issues

### **Performance Optimizations**

#### **Image Management**
```java
// Automatic image compression
private void uploadBitmapToFirebase(Bitmap bitmap, ImageUploadCallback callback) {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos); // 80% quality
}

// Glide caching
Glide.with(context)
     .load(imageUrl)
     .diskCacheStrategy(DiskCacheStrategy.ALL)
     .into(imageView);
```

#### **Lazy Loading**
- RecyclerView with ViewHolder pattern
- Image loading on-demand with Glide
- Database queries with pagination support

### **Key Challenges & Solutions**

#### **1. Firebase Sync Conflicts**
**Problem:** Multiple devices creating conflicting data
**Solution:**
```java
private void showSyncPromptDialog() {
    // User choice: Use Cloud, Sync All, or Use Local
    // Smart conflict resolution with user control
}
```

#### **2. Offline-First Architecture**
**Problem:** Seamless offline/online experience
**Solution:**
```java
public void checkTripSyncStatus(OnBudgetSyncListener listener) {
    if (!userManager.isLoggedIn()) {
        listener.onError("User not logged in");
        return;
    }
    // Intelligent sync status checking
}
```

#### **3. Memory Management**
**Problem:** Large image files causing OOM
**Solution:**
- Automatic bitmap compression
- Glide memory caching
- Proper lifecycle management

### **Firebase Sync Logic**
```java
// Dual-source data strategy
private void loadInitialTripData() {
    if (userManager.isLoggedIn()) {
        loadFirebaseActivitiesDirectNuclear(); // Cloud-first for logged users
    } else {
        loadTripDataLocalOnly(); // Local-only for guests
    }
}

// Conflict resolution
private void syncActivitiesOneByOne(List<TripActivity> firebaseActivities, 
                                   Map<String, TripActivity> localActivityMap, 
                                   TripRepository repository, int index) {
    // Smart merge logic with user preference
}
```

### **Data Consistency Strategy**
- **Optimistic Updates:** Show changes immediately, sync in background
- **Rollback Mechanism:** Revert changes if sync fails
- **User Notifications:** Clear feedback on sync status
- **Queue Management:** Retry failed operations automatically

---

## 6. Architecture Summary

### **MVVM Flow**
```
User Interaction → View → ViewModel → Repository → Data Source
                                        ↓
              UI Update ← LiveData ←────┘
```

### **Tech Stack Summary**
- **Language:** Java 11
- **UI:** XML Layouts + Material Design
- **Database:** Room SQLite + Firebase Firestore
- **Images:** Glide + Firebase Storage
- **Authentication:** Firebase Auth
- **Storage:** SharedPreferences + Room + Firebase
- **Charts:** MPAndroidChart
- **Animations:** Lottie 