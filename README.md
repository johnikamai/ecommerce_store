# 🛍️ ShopEase – Smart E-Commerce Platform

### A Modern E-Commerce Application with Smart Shopping Features and Microservices Architecture

ShopEase is a full-stack e-commerce web application designed to provide a simple, secure, and convenient online shopping experience. It allows customers to explore products, compare prices, manage wishlists, place orders, and receive personalized product suggestions.

The project uses a **React.js frontend, Spring Boot backend, MySQL database, and microservices architecture** to organize different shopping operations into independent services.

## 🎯 Project Objectives

- Provide an easy and user-friendly online shopping platform.
- Simplify product searching, comparison, and purchasing.
- Improve the shopping experience through smart recommendations.
- Support secure login and different user roles.
- Help administrators manage products, customers, staff, and orders.
- Build a flexible system that can be expanded with new features.

## ✨ Key Features

### 👤 Customer Features

- User registration and secure login
- Product browsing with categories and filters
- Smart search with spelling-error correction
- Product comparison
- Shopping cart and wishlist
- Product ratings and reviews
- Order placement and order history
- Order tracking and returns
- Coupons and shopping discounts
- Group orders and split-bill options

### 🤖 Smart Shopping Features

- **AI Shopping Assistant:** Helps customers discover products and answers shopping questions.
- **Personalized Recommendations:** Suggests products based on shopping preferences and available customer history.
- **Image-Based Search:** Finds visually similar products using uploaded images.
- **AI Gift Finder:** Suggests gifts based on interests, occasions, and budget.
- **Review Summarizer:** Highlights important points from customer reviews.
- **Price History:** Displays recorded product price changes.
- **Compatibility Checker:** Helps customers check whether product specifications are compatible.
- **Budget Shopping:** Helps customers shop within their preferred budget.
- **Smart Bundles:** Suggests products that can be purchased together.
- **Wishlist Alerts:** Notifies customers about selected price drops and product restocks.

Some smart features use rule-based calculations and product data rather than trained AI models.

### 🛡️ Admin Features

- Manage products and categories
- Monitor customers and orders
- Manage product inventory
- Approve or reject staff applications
- Assign staff permissions
- Review sales and business reports
- Manage product-related requests

### 👨‍💼 Staff Features

- Staff registration and login
- Administrator approval before accessing staff functions
- Permission-based access to assigned tasks
- Limited product and order management
- Staff account management by administrators

## 💻 Technologies Used

| Technology | Purpose |
|---|---|
| React.js | Frontend development |
| JavaScript | Frontend functionality |
| HTML & CSS | Website structure and styling |
| Tailwind CSS | Responsive user interface |
| Spring Boot | Backend development |
| Spring Cloud Gateway | API request routing |
| Netflix Eureka | Microservice discovery |
| Spring Security & JWT | Authentication and authorization |
| MySQL | Database management |
| REST APIs | Frontend and backend communication |
| Maven | Backend build management |
| Git & GitHub | Version control and collaboration |
| Postman | API testing |

## 🏗️ Microservices Architecture

ShopEase separates major backend operations into independent services.

| Service | Responsibility |
|---|---|
| Discovery Server (Eureka) | Registers and discovers available services |
| API Gateway | Provides a common entry point for frontend requests |
| Authentication Service | Handles login, registration, users, and access control |
| Product Service | Manages products, categories, inventory, and reviews |
| Order Service | Handles orders, wishlists, returns, and group shopping |
| Payment Service | Manages payment records, coupons, and refunds |
| Notification Service | Manages notifications and alerts |
| Recommendation Service | Provides personalized suggestions and shopping assistance |

### How It Works

The customer interacts with the React frontend. Requests are sent through the API Gateway, which forwards them to the appropriate backend service. Eureka helps services discover and communicate with one another.

Each business service maintains responsibility for its own data, making the system easier to maintain and extend.

## 📁 Project Structure

```text
ecommerce_store/
│
├── frontend/              # React frontend
├── api-gateway/           # API Gateway
├── discovery-server/      # Eureka Server
├── services/
│   ├── auth-service/
│   ├── product-service/
│   ├── order-service/
│   ├── payment-service/
│   ├── notification-service/
│   └── recommendation-service/
│
├── backend/               # Legacy backend and Maven wrapper
├── docs/                  # Documentation
├── tools/                 # Migration and utility scripts
├── start-services.ps1     # Local service launcher
└── README.md
```

## 🚀 How to Run the Project

These instructions apply to the `feature/microservices-migration` branch.

### Prerequisites

Make sure the following tools are installed:

- Java 21 or later
- Node.js 22.12 or later
- MySQL 8
- Python
- Git

### 1. Clone the Repository

```bash
git clone https://github.com/johnikamai/ecommerce_store.git
cd ecommerce_store
git checkout feature/microservices-migration
```

### 2. Configure the Database

Start MySQL and configure your local database connection and service credentials.

Keep database passwords and authentication keys in the ignored `.local/application.properties` file or environment variables. Do not upload secrets to GitHub.

For a fresh setup, follow the migration instructions in the repository documentation.

### 3. Start the Backend Services

In Windows PowerShell, run:

```powershell
.\start-services.ps1 -Build
```

The script builds and launches the microservices, API Gateway, and Eureka Server.

### 4. Open the Application

After all services start successfully, open:

```text
http://localhost:5173
```

### 5. Run Tests

Frontend tests:

```powershell
cd frontend
npm test
```

Integration tests from the project root:

```powershell
node run_integration_tests.js
```

## 🔒 Security

ShopEase includes JWT-based authentication and role-based access control for customers, administrators, and staff.

Staff accounts require administrator approval, and their available operations depend on assigned permissions.

Sensitive settings such as database passwords, signing keys, and service credentials should never be committed to the repository.

## 📝 Important Notes

- Card and UPI payment flows are demonstration features, not real-money payment integrations.
- External email and SMS delivery requires additional provider configuration.
- Some intelligent shopping tools use product data, rules, and calculations.
- Cloudflare Quick Tunnel links are temporary and are not permanent hosting addresses.
- The microservices implementation is documented and tested locally; production deployment requires separate setup and validation.

## 🔮 Future Enhancements

- Dedicated Android and iOS mobile applications
- Voice-based shopping
- Virtual product try-on
- Faster and more detailed delivery tracking
- Additional regional language support
- More advanced personalized shopping suggestions
- Integration with real payment providers
- Improved customer support and shopping assistance

## 📚 Documentation

Additional information is available in the `docs/` folder, including:

- Microservices architecture and migration details
- Feature verification reports
- Smart shopping feature documentation
- Deployment preparation guides
- Database migration information

## 🎓 Project Information

**Project Name:** ShopEase – Smart E-Commerce Platform

**Project Type:** Full-Stack Web Application

**Domain:** E-Commerce and Smart Shopping

**Architecture:** Microservices

**Repository:** https://github.com/johnikamai/ecommerce_store

---

### ⭐ Conclusion

ShopEase combines essential e-commerce functionality with smart shopping tools and a modular backend architecture. It aims to make online shopping easier for customers while giving administrators and staff better control over daily store operations.

**ShopEase – Smart Shopping Made Simple.** 🛍️# 🛍️ ShopEase – Smart E-Commerce Platform

### A Modern E-Commerce Application with Smart Shopping Features and Microservices Architecture

ShopEase is a full-stack e-commerce web application designed to provide a simple, secure, and convenient online shopping experience. It allows customers to explore products, compare prices, manage wishlists, place orders, and receive personalized product suggestions.

The project uses a **React.js frontend, Spring Boot backend, MySQL database, and microservices architecture** to organize different shopping operations into independent services.

## 🎯 Project Objectives

- Provide an easy and user-friendly online shopping platform.
- Simplify product searching, comparison, and purchasing.
- Improve the shopping experience through smart recommendations.
- Support secure login and different user roles.
- Help administrators manage products, customers, staff, and orders.
- Build a flexible system that can be expanded with new features.

## ✨ Key Features

### 👤 Customer Features

- User registration and secure login
- Product browsing with categories and filters
- Smart search with spelling-error correction
- Product comparison
- Shopping cart and wishlist
- Product ratings and reviews
- Order placement and order history
- Order tracking and returns
- Coupons and shopping discounts
- Group orders and split-bill options

### 🤖 Smart Shopping Features

- **AI Shopping Assistant:** Helps customers discover products and answers shopping questions.
- **Personalized Recommendations:** Suggests products based on shopping preferences and available customer history.
- **Image-Based Search:** Finds visually similar products using uploaded images.
- **AI Gift Finder:** Suggests gifts based on interests, occasions, and budget.
- **Review Summarizer:** Highlights important points from customer reviews.
- **Price History:** Displays recorded product price changes.
- **Compatibility Checker:** Helps customers check whether product specifications are compatible.
- **Budget Shopping:** Helps customers shop within their preferred budget.
- **Smart Bundles:** Suggests products that can be purchased together.
- **Wishlist Alerts:** Notifies customers about selected price drops and product restocks.

Some smart features use rule-based calculations and product data rather than trained AI models.

### 🛡️ Admin Features

- Manage products and categories
- Monitor customers and orders
- Manage product inventory
- Approve or reject staff applications
- Assign staff permissions
- Review sales and business reports
- Manage product-related requests

### 👨‍💼 Staff Features

- Staff registration and login
- Administrator approval before accessing staff functions
- Permission-based access to assigned tasks
- Limited product and order management
- Staff account management by administrators

## 💻 Technologies Used

| Technology | Purpose |
|---|---|
| React.js | Frontend development |
| JavaScript | Frontend functionality |
| HTML & CSS | Website structure and styling |
| Tailwind CSS | Responsive user interface |
| Spring Boot | Backend development |
| Spring Cloud Gateway | API request routing |
| Netflix Eureka | Microservice discovery |
| Spring Security & JWT | Authentication and authorization |
| MySQL | Database management |
| REST APIs | Frontend and backend communication |
| Maven | Backend build management |
| Git & GitHub | Version control and collaboration |
| Postman | API testing |

## 🏗️ Microservices Architecture

ShopEase separates major backend operations into independent services.

| Service | Responsibility |
|---|---|
| Discovery Server (Eureka) | Registers and discovers available services |
| API Gateway | Provides a common entry point for frontend requests |
| Authentication Service | Handles login, registration, users, and access control |
| Product Service | Manages products, categories, inventory, and reviews |
| Order Service | Handles orders, wishlists, returns, and group shopping |
| Payment Service | Manages payment records, coupons, and refunds |
| Notification Service | Manages notifications and alerts |
| Recommendation Service | Provides personalized suggestions and shopping assistance |

### How It Works

The customer interacts with the React frontend. Requests are sent through the API Gateway, which forwards them to the appropriate backend service. Eureka helps services discover and communicate with one another.

Each business service maintains responsibility for its own data, making the system easier to maintain and extend.

## 📁 Project Structure

```text
ecommerce_store/
│
├── frontend/              # React frontend
├── api-gateway/           # API Gateway
├── discovery-server/      # Eureka Server
├── services/
│   ├── auth-service/
│   ├── product-service/
│   ├── order-service/
│   ├── payment-service/
│   ├── notification-service/
│   └── recommendation-service/
│
├── backend/               # Legacy backend and Maven wrapper
├── docs/                  # Documentation
├── tools/                 # Migration and utility scripts
├── start-services.ps1     # Local service launcher
└── README.md
```

## 🚀 How to Run the Project

These instructions apply to the `feature/microservices-migration` branch.

### Prerequisites

Make sure the following tools are installed:

- Java 21 or later
- Node.js 22.12 or later
- MySQL 8
- Python
- Git

### 1. Clone the Repository

```bash
git clone https://github.com/johnikamai/ecommerce_store.git
cd ecommerce_store
git checkout feature/microservices-migration
```

### 2. Configure the Database

Start MySQL and configure your local database connection and service credentials.

Keep database passwords and authentication keys in the ignored `.local/application.properties` file or environment variables. Do not upload secrets to GitHub.

For a fresh setup, follow the migration instructions in the repository documentation.

### 3. Start the Backend Services

In Windows PowerShell, run:

```powershell
.\start-services.ps1 -Build
```

The script builds and launches the microservices, API Gateway, and Eureka Server.

### 4. Open the Application

After all services start successfully, open:

```text
http://localhost:5173
```

### 5. Run Tests

Frontend tests:

```powershell
cd frontend
npm test
```

Integration tests from the project root:

```powershell
node run_integration_tests.js
```

## 🔒 Security

ShopEase includes JWT-based authentication and role-based access control for customers, administrators, and staff.

Staff accounts require administrator approval, and their available operations depend on assigned permissions.

Sensitive settings such as database passwords, signing keys, and service credentials should never be committed to the repository.

## 📝 Important Notes

- Card and UPI payment flows are demonstration features, not real-money payment integrations.
- External email and SMS delivery requires additional provider configuration.
- Some intelligent shopping tools use product data, rules, and calculations.
- Cloudflare Quick Tunnel links are temporary and are not permanent hosting addresses.
- The microservices implementation is documented and tested locally; production deployment requires separate setup and validation.

## 🔮 Future Enhancements

- Dedicated Android and iOS mobile applications
- Voice-based shopping
- Virtual product try-on
- Faster and more detailed delivery tracking
- Additional regional language support
- More advanced personalized shopping suggestions
- Integration with real payment providers
- Improved customer support and shopping assistance

## 📚 Documentation

Additional information is available in the `docs/` folder, including:

- Microservices architecture and migration details
- Feature verification reports
- Smart shopping feature documentation
- Deployment preparation guides
- Database migration information

## 🎓 Project Information

**Project Name:** ShopEase – Smart E-Commerce Platform

**Project Type:** Full-Stack Web Application

**Domain:** E-Commerce and Smart Shopping

**Architecture:** Microservices

**Repository:** https://github.com/johnikamai/ecommerce_store

---

### ⭐ Conclusion

ShopEase combines essential e-commerce functionality with smart shopping tools and a modular backend architecture. It aims to make online shopping easier for customers while giving administrators and staff better control over daily store operations.

**ShopEase – Smart Shopping Made Simple.** 🛍️
