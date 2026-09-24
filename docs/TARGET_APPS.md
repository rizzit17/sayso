# Target Applications Declaration

**Samsung PRISM GenAI Hackathon 3.0 — Theme 3: Teachable Voice Automation**

This document officially declares the target Android applications validated for the PRISM (SaySo) teachable voice automation engine, per Evaluation Criteria T1–T9 and §8 of `master prompt.md`.

---

## 1. Primary Target Applications (Validated in Test Suite & Replay Engine)

### A. Zomato (Food Delivery)
* **Package Name**: `com.application.zomato`
* **Target Flows**:
  1. Search for restaurant / dish (e.g., "Domino's Pizza", "Margherita pizza", "Farmhouse pizza").
  2. Select restaurant card from dynamic search results.
  3. Add item to cart with quantity selection.
  4. Proceed to checkout up to the payment boundary.
* **Safety Boundary Enforcement**: Halts automatically upon reaching checkout payment selection / UPI PIN / Card entry screen (`PaymentSelectionActivity`, `CheckoutActivity`). Zero clicks or text dispatched on payment screens.

### B. Domino's Pizza (Food Delivery via Zomato / Native)
* **Package Name**: `com.dominospizza.ordering` / `com.application.zomato`
* **Target Flows**:
  1. Outlet selection (e.g., "MG Road", "Indiranagar").
  2. Pizza category and crust customization.
  3. Cart addition.

### C. Amazon India (E-Commerce)
* **Package Name**: `in.amazon.mShop.android.shopping`
* **Target Flows**:
  1. Search for product (e.g., "Sony WH-1000XM5 headphones", "Laptop stand").
  2. Select first or matching product search result.
  3. Add to Cart / Buy Now.
* **Safety Boundary Enforcement**: Halts before OTP, CVV, Card number, or Net Banking credentials on Amazon Pay / gateway screens.

### D. Swiggy (Food & Grocery Delivery — Cross-App Generalization [B2])
* **Package Name**: `in.swiggy.android`
* **Target Flows**:
  1. Cross-app generalization from Zomato flow using semantic role (`search_box`, `card`, `button`) and domain concept (`search_input`, `dish_title`, `add_to_cart`).
  2. Parameterized execution across platforms.

---

## 2. Generalization & Zero-SDK Architecture

The PRISM engine interacts with applications **strictly via the native Android Accessibility Service** (`AccessibilityNodeInfo`, `performAction`, `dispatchGesture`).
* **Zero App-Specific SDKs**: No Zomato, Amazon, or Swiggy SDKs or partner APIs are included.
* **Zero Deep Links**: Navigation and actions are executed through UI observation and accessibility actions, mimicking natural human taps and text inputs.
* **Zero Hard-Coded Flow Scripts**: Workflows are taught dynamically by the user and generalized into parameterized slot schemas at runtime.
