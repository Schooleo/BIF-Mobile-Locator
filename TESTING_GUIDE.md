# Android Unit Testing Guide

This guide explains how to write, run, and automate tests for the BIF Mobile Locator project.

## 1. Types of Tests

In Android, there are two main categories of tests:

### A. Local Unit Tests (`src/test`)

- **Where**: `app/src/test/java/`
- **What**: Pure Java tests that run on your development machine's JVM (not on an Android device).
- **Speed**: Very Fast.
- **Use Case**: Testing business logic, calculations, and utility classes that don't depend on Android APIs (e.g., `Context`, `View`).
- **Example**: `DistanceUtilsTest.java`

### B. Instrumented Tests (`src/androidTest`)

- **Where**: `app/src/androidTest/java/`
- **What**: Tests that run on a hardware device or emulator.
- **Speed**: Slow.
- **Use Case**: Testing UI interactions (clicking buttons), accessing database/file system, or checking how the app behaves on a real OS.

---

## 2. Anatomy of a Unit Test

We use **JUnit 4** as our testing framework.

### Key Annotations

- `@Test`: Marks a method as a test case.
- `@Before`: Runs before _each_ test (used for setup, e.g., initializing objects).
- `@After`: Runs after _each_ test (used for teardown, e.g., closing streams).
- `@BeforeClass`: Runs once before _all_ tests in the class (must be static).
- `@AfterClass`: Runs once after _all_ tests in the class (must be static).

### Commons Assertions

JUnit provides various methods to assert expected results.

| Assertion                             | Description                                                               | Example                                  |
| :------------------------------------ | :------------------------------------------------------------------------ | :--------------------------------------- |
| `assertEquals(expected, actual)`      | Checks if two values are equal. For doubles, provide a delta (tolerance). | `assertEquals(4, 2+2);`                  |
| `assertNotEquals(unexpected, actual)` | Checks if values are NOT equal.                                           | `assertNotEquals(0, result);`            |
| `assertTrue(condition)`               | Checks if a condition is true.                                            | `assertTrue(list.isEmpty());`            |
| `assertFalse(condition)`              | Checks if a condition is false.                                           | `assertFalse(list.contains("invalid"));` |
| `assertNotNull(object)`               | Checks that an object is not null.                                        | `assertNotNull(resultObj);`              |
| `assertNull(object)`                  | Checks that an object IS null.                                            | `assertNull(error);`                     |
| `assertSame(expected, actual)`        | Checks if two references point to the **same object** in memory.          | `assertSame(obj1, obj1);`                |

### Testing Exceptions

To verify that code throws an expected exception (e.g., invalid input):

```java
@Test(expected = IllegalArgumentException.class)
public void testInvalidInput_throwsException() {
    // This code is expected to throw IllegalArgumentException
    DistanceUtils.calculateDistance(-1000, 0, 0, 0);
}
```

### Example Test Class

```java
@Test
public void calculateDistance_samePoint_returnsZero() {
    // 1. Arrange (Setup inputs)
    double lat = 10.0;

    // 2. Act (Run the code)
    double result = DistanceUtils.calculateDistance(lat, 20.0, lat, 20.0);

    // 3. Assert (Verify output)
    assertEquals("Distance should be 0", 0.0, result, 0.01);
}
```

---

## 3. Running Tests

### In Android Studio

1.  **Right-click** on a file (e.g., `DistanceUtilsTest`) or directory (`src/test`).
2.  Select **Run 'DistanceUtilsTest'**.
3.  View results in the **Run** window at the bottom.

### Via Command Line

- Run all unit tests:
  ```bash
  ./gradlew test
  ```
- Run specific test:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.bif.locator.utils.DistanceUtilsTest"
  ```

### Viewing Test Reports

After running tests via command line, a detailed HTML report is generated. You can open it immediately with the command (from the root directory):

**Windows (PowerShell/CMD):**

```powershell
start app/build/reports/tests/testDebugUnitTest/index.html
```

**Or run the combination:**

```powershell
./gradlew test
start app/build/reports/tests/testDebugUnitTest/index.html
```

---

## 4. CI/CD Integration

Our GitHub Actions pipeline (`.github/workflows/android.yml`) automatically protects the codebase.

- **Job**: `unit_test`
- **Command**: `./gradlew test`
- **Effect**:
  - Every time you push code or open a Pull Request, GitHub runs all unit tests.
  - If **any** test fails, the build turns **red**, and deployment is blocked.
  - This ensures no broken logic is ever deployed to testers.

---

## 5. Naming Standards

To make tests readable and easy to debug, we use a structured **3-part naming convention** for test methods. This tells you exactly _what_ failed and _why_ without opening the code.

**Format**: `UnitOfWork_StateUnderTest_ExpectedBehavior`

### Part 1: Unit of Work

- **What**: The specific method, class, or feature being tested.
- **Example**: `calculateDistance`, `login`, `isValidEmail`

### Part 2: State Under Test

- **What**: The specific condition, input, or scenario you are setting up.
- **Context**: "With invalid password", "When network is down", "With generic inputs".
- **Example**: `samePoint`, `invalidPassword`, `nullInput`

### Part 3: Expected Behavior

- **What**: The result you expect from the Unit of Work.
- **Result**: What does the method return? What state changes? Does it throw an exception?
- **Example**: `returnsZero`, `showsErrorMessage`, `throwsException`

### Examples

| Bad Name    | Good Name                                  | Why?                                  |
| :---------- | :----------------------------------------- | :------------------------------------ |
| `test1`     | `calculateDistance_samePoint_returnsZero`  | Explicitly states logic.              |
| `testLogin` | `login_invalidCredentials_showsToast`      | Specifies the scenario and UI result. |
| `emailTest` | `validateEmail_missingAtSign_returnsFalse` | Describes the edge case being tested. |

---

## 6. Best Practices

1.  **One Concept per Test**: Each test method should verify one specific behavior.
2.  **Descriptive Names**: `calculateDistance_samePoint_returnsZero` is better than `test1`.
3.  **Fast Feedback**: Run local unit tests frequently while coding.
4.  **Mock Dependencies**: When your code relies on Android classes (like `Context`), use a mocking framework like **Mockito** instead of relying on real device states.

---

## 7. Mocking with Mockito

**Mockito** is a framework that lets you create "fake" versions of dependencies. This is crucial when testing classes that depend on Android APIs (like `Context`, `SharedPreferences`, or `LocationManager`) which don't exist in a pure local JVM environment.

### Core Concepts

- **Mock**: A fake object that tracks interactions but does nothing by default.
- **Stub (`when`)**: Telling the mock what to return when a method is called.
- **Verify**: Checking if a method was called on the mock.

### Example Scenario

Imagine a `LocationTracker` class that depends on Android's `LocationManager`.

```java
import static org.mockito.Mockito.*;

public class LocationTrackerTest {

    @Test
    public void trackLocation_providerEnabled_requestsUpdates() {
        // 1. Create a Mock of the Android dependency
        LocationManager mockLocManager = mock(LocationManager.class);

        // 2. Stub behavior: Tell the mock to return 'true' when isProviderEnabled is called
        when(mockLocManager.isProviderEnabled(LocationManager.GPS_PROVIDER)).thenReturn(true);

        // 3. Create the class under test, injecting the mock
        LocationTracker tracker = new LocationTracker(mockLocManager);
        tracker.startTracking();

        // 4. Verify that the tracker actually called requestLocationUpdates on the mock
        verify(mockLocManager).requestLocationUpdates(anyString(), anyLong(), anyFloat(), any());
    }
}
```

### Tips

- Use `mockito-inline` (already included) to mock `final` classes and static methods if absolutely necessary.
- Mock external dependencies (Database, Network), not your own data objects (Data classes).
