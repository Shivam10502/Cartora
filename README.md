
````markdown
# Cartora 🛒

Cartora is a backend e-commerce application built using Spring Boot.  
The project is designed to provide a secure and scalable REST API for managing users, products, categories, shopping carts, orders, and the complete e-commerce workflow.

The project is being developed with a focus on clean architecture, RESTful API design, database relationships, authentication, authorization, transaction management, and real-world e-commerce business rules.

---

## 🚀 Tech Stack

### Backend
- Java 21
- Spring Boot
- Spring Web
- Spring Data JPA
- Spring Security
- JWT Authentication
- Lombok
- Bean Validation

### Database
- PostgreSQL
- Hibernate / JPA

### Development & Testing
- IntelliJ IDEA
- Postman
- Git & GitHub
- Maven

---

# 📌 Project Status

The project is currently under active development.

### Completed

- [x] Project setup
- [x] PostgreSQL database configuration
- [x] User registration
- [x] User authentication
- [x] Password encryption using BCrypt
- [x] JWT authentication
- [x] Spring Security configuration
- [x] Custom authentication entry point
- [x] Global exception handling
- [x] Category management
- [x] Product management
- [x] Shopping cart
- [x] Cart item management
- [x] Stock validation
- [x] Order creation
- [x] Order history
- [x] Order details
- [x] Order cancellation
- [x] Stock restoration on order cancellation
- [x] Postman API testing

### Planned

- [ ] Checkout module
- [ ] Payment module
- [ ] User profile management
- [ ] Address management
- [ ] Admin-specific functionality
- [ ] Final role-based authorization
- [ ] Product search
- [ ] Product filtering
- [ ] Pagination and sorting
- [ ] Comprehensive unit tests
- [ ] Integration tests
- [ ] Swagger / OpenAPI documentation
- [ ] Docker configuration
- [ ] Deployment
- [ ] Security hardening
- [ ] Performance optimization

---

# 🏗️ Architecture

Cartora follows a layered Spring Boot architecture.

```text
Controller
    ↓
Service
    ↓
Repository
    ↓
Database
````

### Controller Layer

Responsible for:

* Handling HTTP requests
* Request validation
* Returning API responses
* Mapping requests to service methods

### Service Layer

Responsible for:

* Business logic
* Validation
* Transaction management
* Entity relationships
* Stock management
* Order processing

### Repository Layer

Responsible for:

* Database operations
* Entity persistence
* Custom queries

### Entity Layer

Contains JPA entities representing database tables and relationships.

### DTO Layer

Used to separate API request/response models from database entities.

---

# 📂 Project Structure

```text
src
└── main
    ├── java
    │   └── com.ecom.cartora
    │       │
    │       ├── auth
    │       │
    │       ├── cart
    │       │   ├── dto
    │       │   ├── entity
    │       │   ├── CartController
    │       │   ├── CartService
    │       │   ├── CartRepo
    │       │   └── CartItemRepo
    │       │
    │       ├── category
    │       │   ├── dto
    │       │   ├── entity
    │       │   ├── CategoryController
    │       │   ├── CategoryService
    │       │   └── CategoryRepo
    │       │
    │       ├── exception
    │       │
    │       ├── order
    │       │   ├── dto
    │       │   ├── entity
    │       │   ├── repository
    │       │   ├── OrderController
    │       │   └── OrderService
    │       │
    │       ├── product
    │       │   ├── dto
    │       │   ├── Product
    │       │   ├── ProductController
    │       │   ├── ProductService
    │       │   └── ProductRepo
    │       │
    │       ├── security
    │       │   ├── JwtFilter
    │       │   ├── JwtService
    │       │   ├── SecurityConfig
    │       │   └── CustomAuthenticationEntryPoint
    │       │
    │       └── user
    │           ├── User
    │           ├── UserRepo
    │           └── CustomUserDetailsService
    │
    └── resources
        └── application.properties
```

---

# 🔐 Authentication & Security

Cartora uses **Spring Security + JWT** for authentication.

## Authentication Flow

```text
User Registration
       ↓
Password encoded using BCrypt
       ↓
User stored in PostgreSQL
       ↓
User Login
       ↓
AuthenticationManager
       ↓
UserDetailsService
       ↓
Password verification
       ↓
JWT generated
       ↓
Client stores JWT
       ↓
JWT sent with protected requests
       ↓
JwtFilter validates token
       ↓
SecurityContext populated
       ↓
Request allowed
```

Protected endpoints require:

```http
Authorization: Bearer <JWT>
```

---

## 🔑 User Roles

The application currently supports:

```text
CUSTOMER
ADMIN
```

Role-based authorization is planned for the final security pass.

Currently, authenticated users can access protected endpoints, while detailed ADMIN/CUSTOMER endpoint restrictions will be finalized later.

---

# 👤 User Module

The User module handles:

* User registration
* User authentication
* Password encryption
* User roles
* JWT authentication

### User Roles

```java
CUSTOMER
ADMIN
```

User email is unique in the database.

---

# 📁 Category Module

The Category module provides CRUD functionality for product categories.

### Features

* Create category
* Get category
* Get all categories
* Update category
* Delete category
* Duplicate category name validation

Category names have a database-level unique constraint.

---

# 📦 Product Module

The Product module manages products associated with categories.

### Product Fields

```text
id
name
description
price
stockQuantity
sku
category
createdAt
updatedAt
```

### Features

* Create product
* Get product
* Get all products
* Update product
* Delete product
* Category validation
* SKU uniqueness validation
* Price validation
* Stock validation

Each product belongs to one category.

---

# 🛒 Cart Module

The Cart module manages the authenticated user's shopping cart.

## Relationships

```text
User 1 ───── 1 Cart

Cart 1 ───── * CartItem

CartItem * ───── 1 Product
```

### Cart

A user can have one cart.

### CartItem

A cart can contain multiple products.

A product cannot appear multiple times as separate cart items in the same cart.

A unique constraint is used on:

```text
cart_id + product_id
```

---

## Cart Features

* Add product to cart
* Get current user's cart
* Update cart item quantity
* Remove cart item
* Clear cart
* Validate product stock
* Prevent users from modifying another user's cart items

### Important Stock Rule

Adding a product to the cart **does not reduce product stock**.

Stock is reduced only when an order is successfully created.

```text
Add to Cart
    ↓
Validate available stock
    ↓
Stock remains unchanged
```

When an order is created:

```text
Create Order
    ↓
Reduce Product Stock
```

---

# 📦 Order Module

The Order module converts a user's cart into a purchase order.

## Order Relationships

```text
User 1 ───── * Order

Order 1 ───── * OrderItem

OrderItem * ───── 1 Product
```

---

## Order Entity

An order contains:

```text
id
user
totalAmount
status
createdAt
updatedAt
```

---

## Order Status

```java
PENDING
CONFIRMED
CANCELLED
SHIPPED
DELIVERED
```

---

# 🧾 OrderItem

Each order contains one or more order items.

An OrderItem contains:

```text
id
order
product
quantity
price
subtotal
```

### Historical Price Snapshot

The product's price is copied into `OrderItem.price` when the order is created.

For example:

```text
Product price at purchase = ₹799
```

OrderItem stores:

```text
price = ₹799
```

If the product price later changes to:

```text
₹999
```

the existing order still contains:

```text
₹799
```

This preserves the historical price of the purchase.

---

# 🧮 Order Creation Flow

The order creation process follows this flow:

```text
Customer
   ↓
Get authenticated user
   ↓
Get user's cart
   ↓
Check cart is not empty
   ↓
Validate stock for every cart item
   ↓
Create Order
   ↓
Convert CartItems → OrderItems
   ↓
Snapshot product prices
   ↓
Calculate item subtotals
   ↓
Calculate total order amount
   ↓
Reduce product stock
   ↓
Clear cart
   ↓
Return OrderResponse
```

The operation is transactional.

If a failure occurs during the transaction, the changes are rolled back.

---

# ❌ Order Cancellation

Customers can cancel orders that are still:

```text
PENDING
```

Orders with the following statuses cannot be cancelled:

```text
CONFIRMED
SHIPPED
DELIVERED
CANCELLED
```

When a pending order is cancelled:

```text
Order status
     ↓
CANCELLED

Product stock
     ↓
Restored
```

The original OrderItem quantity remains unchanged because it represents the historical purchase.

---

# 🔄 Order Cancellation Flow

```text
Customer
   ↓
Find Order
   ↓
Verify Order belongs to Customer
   ↓
Check Order status
   ↓
Restore Product Stock
   ↓
Set status = CANCELLED
   ↓
Update timestamp
   ↓
Return OrderResponse
```

---

# 📊 Current Database Relationships

The current core database relationships are:

```text
                    ┌──────────────┐
                    │     User     │
                    └──────┬───────┘
                           │
                    1      │      *
                           │
                    ┌──────▼───────┐
                    │    Order     │
                    └──────┬───────┘
                           │
                    1      │      *
                           │
                    ┌──────▼───────┐
                    │  OrderItem   │
                    └──────┬───────┘
                           │
                           │ *
                           │
                           │ 1
                    ┌──────▼───────┐
                    │   Product    │
                    └──────┬───────┘
                           │
                           │ *
                           │
                           │ 1
                    ┌──────▼───────┐
                    │   Category   │
                    └──────────────┘


                    ┌──────────────┐
                    │     User     │
                    └──────┬───────┘
                           │
                    1      │      1
                           │
                    ┌──────▼───────┐
                    │     Cart     │
                    └──────┬───────┘
                           │
                    1      │      *
                           │
                    ┌──────▼───────┐
                    │   CartItem   │
                    └──────┬───────┘
                           │
                           │ *
                           │
                           │ 1
                    ┌──────▼───────┐
                    │   Product    │
                    └──────────────┘
```

---

# 🌐 Current API Endpoints

Base URL:

```text
http://localhost:8080
```

---

## Authentication

### Register

```http
POST /api/users/register
```

Example:

```json
{
    "name": "John Customer",
    "email": "john@cartora.com",
    "password": "Customer@123",
    "role": "CUSTOMER"
}
```

### Login

```http
POST /api/auth/login
```

Example:

```json
{
    "email": "john@cartora.com",
    "password": "Customer@123"
}
```

---

# 🗂️ Category APIs

```http
POST   /api/categories
GET    /api/categories
GET    /api/categories/{categoryId}
PUT    /api/categories/{categoryId}
DELETE /api/categories/{categoryId}
```

---

# 📦 Product APIs

```http
POST   /api/products
GET    /api/products
GET    /api/products/{productId}
PUT    /api/products/{productId}
DELETE /api/products/{productId}
```

---

# 🛒 Cart APIs

### Add Product

```http
POST /api/cart/items
```

Example:

```json
{
    "productId": 1,
    "quantity": 2
}
```

### Get Cart

```http
GET /api/cart
```

### Update Cart Item

```http
PUT /api/cart/items/{cartItemId}
```

Example:

```json
{
    "quantity": 4
}
```

> `cartItemId` must be used here, not `productId` or `cartId`.

### Remove Cart Item

```http
DELETE /api/cart/items/{cartItemId}
```

### Clear Cart

```http
DELETE /api/cart
```

---

# 📦 Order APIs

### Create Order

```http
POST /api/orders
```

No request body is required.

The order is created from the authenticated user's current cart.

### Get My Orders

```http
GET /api/orders
```

### Get Order By ID

```http
GET /api/orders/{orderId}
```

### Cancel Order

```http
PUT /api/orders/{orderId}/cancel
```

---

# 🧪 API Testing

Postman is currently being used for API testing.

The recommended testing flow is:

```text
Register Admin
       ↓
Login Admin
       ↓
Create Category
       ↓
Create Products
       ↓
Register Customer
       ↓
Login Customer
       ↓
Add Products to Cart
       ↓
Update Cart
       ↓
Create Order
       ↓
Check Product Stock
       ↓
Check Cart
       ↓
Get Orders
       ↓
Get Order Details
       ↓
Create Another Order
       ↓
Cancel Order
       ↓
Verify Stock Restoration
```

---

# 🔒 Security Testing

The application has been tested for:

* Invalid login credentials
* JWT authentication
* Protected endpoints
* Customer authentication
* Admin authentication
* Unauthorized access
* Cart ownership
* Order ownership

Final role-based authorization rules will be implemented during the security hardening phase.

---

# ⚠️ Exception Handling

The project contains custom exceptions for business-specific errors.

Examples include:

```text
CategoryAlreadyExistsException
CategoryNotFoundException
ProductNotFoundException
CartNotFoundException
CartItemNotFoundException
CartIsEmptyException
InsufficientStockException
OrderNotFoundException
OrderCancellationException
```

A global exception handler is used to provide consistent API error responses.

---

# 💾 Database Constraints

Database-level constraints are used in addition to application-level validation.

Examples include:

### User

```text
uk_user_email
```

Ensures user email addresses are unique.

### Category

```text
uk_category_name
```

Ensures category names are unique.

### Product

```text
uk_product_sku
```

Ensures product SKUs are unique.

### Cart

```text
uk_cart_user
```

Ensures one cart per user.

### CartItem

```text
uk_cart_product
```

Ensures a product can only occur once in a specific cart.

---

# 🔄 Transaction Management

`@Transactional` is used for operations where multiple database changes must succeed or fail together.

Examples:

### Cart

```text
Add product
Update quantity
Remove item
Clear cart
```

### Order

```text
Create order
Cancel order
```

This is particularly important during order creation.

For example:

```text
Create Order
     ↓
Create OrderItems
     ↓
Reduce Product Stock
     ↓
Clear Cart
```

If one operation fails, the transaction can roll back the previous changes.

---

# 🧠 Important Business Rules

### Cart

* A user has one cart.
* A cart can contain multiple cart items.
* The same product cannot appear twice in one cart.
* Adding to the cart does not reduce stock.
* Cart quantity cannot exceed available stock.

### Order

* An order belongs to one user.
* A user can have multiple orders.
* An order contains one or more order items.
* OrderItem stores the historical purchase price.
* Stock is reduced when an order is created.
* Cart items are cleared after successful order creation.
* Only pending orders can currently be cancelled.
* Cancelling an order restores the purchased quantity to product stock.
* OrderItem quantity is not modified when an order is cancelled.

---

# 📈 Future Improvements

The following improvements are planned as the project evolves.

## Checkout

* Shipping address
* Billing information
* Order confirmation
* Checkout validation

## Payment

* Payment gateway integration
* Payment status
* Payment transaction records
* Payment failure handling
* Refund handling

## User Profile

* Update profile
* Change password
* Address management
* Multiple saved addresses

## Admin

* Admin dashboard
* Product management
* Category management
* Order management
* User management
* Inventory management
* Role-based endpoint authorization

## Product Search

* Search by product name
* Category filtering
* Price filtering
* Stock filtering
* Sorting
* Pagination

## Testing

* Unit tests
* Service tests
* Controller tests
* Repository tests
* Integration tests
* Security tests

## API Documentation

* Swagger / OpenAPI
* API examples
* Authentication documentation

## Deployment

* Docker
* Docker Compose
* Production PostgreSQL
* Environment variables
* CI/CD
* Cloud deployment

---

# 🐳 Planned Docker Architecture

The planned production-style setup is:

```text
             ┌───────────────┐
             │    Client     │
             └───────┬───────┘
                     │
                     ▼
             ┌───────────────┐
             │ Cartora API   │
             │ Spring Boot   │
             └───────┬───────┘
                     │
                     ▼
             ┌───────────────┐
             │  PostgreSQL   │
             └───────────────┘
```

Docker and deployment will be added later in the project.

---

# 🛠️ Running the Project Locally

## Prerequisites

Install:

* Java 21
* Maven
* PostgreSQL
* IntelliJ IDEA or another Java IDE
* Postman

---

## Database Setup

Create a PostgreSQL database:

```sql
CREATE DATABASE cartora;
```

Configure the database credentials in:

```text
src/main/resources/application.properties
```

Example:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/cartora
spring.datasource.username=YOUR_USERNAME
spring.datasource.password=YOUR_PASSWORD
```

JWT configuration should also be provided through application configuration.

---

# ▶️ Running the Application

Clone the repository:

```bash
git clone <your-repository-url>
```

Navigate into the project:

```bash
cd Cartora
```

Run the application using Maven:

```bash
./mvnw spring-boot:run
```

On Windows:

```bash
mvnw.cmd spring-boot:run
```

The application will start on:

```text
http://localhost:8080
```

---

# 📮 Postman

The project uses Postman for API testing.

A typical Postman environment can contain:

```text
baseUrl
adminJwt
customerJwt
```

Protected requests should use:

```text
Authorization
Bearer Token
{{customerJwt}}
```

or:

```text
Authorization
Bearer Token
{{adminJwt}}
```

depending on the operation being tested.

---

# 🧪 Example E-Commerce Flow

A complete example flow:

```text
1. Register customer
        ↓
2. Login
        ↓
3. Receive JWT
        ↓
4. View products
        ↓
5. Add products to cart
        ↓
6. Update cart quantity
        ↓
7. Create order
        ↓
8. Product stock decreases
        ↓
9. Cart becomes empty
        ↓
10. View order
        ↓
11. Cancel pending order
        ↓
12. Product stock is restored
```

---

# 📌 Development Philosophy

Cartora is being developed incrementally with emphasis on understanding the underlying concepts rather than simply implementing features.

Key areas of focus include:

* Spring Boot fundamentals
* REST API design
* JPA relationships
* Database constraints
* Spring Security
* JWT authentication
* Transaction management
* Exception handling
* DTO-based API design
* E-commerce business logic
* Clean and maintainable service-layer design
* Testing
* Production readiness

---

# 🔮 Long-Term Goal

The goal of Cartora is to evolve from a basic Spring Boot e-commerce API into a production-style backend application with:

```text
Authentication
      +
Authorization
      +
Product Management
      +
Inventory
      +
Shopping Cart
      +
Checkout
      +
Payments
      +
Orders
      +
User Management
      +
Search & Filtering
      +
Testing
      +
Documentation
      +
Docker
      +
Deployment
```

The final application should demonstrate practical backend engineering skills suitable for real-world software development and backend engineering interviews.

---

# 👨‍💻 Author

**Shivam Upadhyay**

Cartora — E-Commerce Backend

Built with Java + Spring Boot + PostgreSQL.

```

### One correction I intentionally made

I marked **Order History as completed** because your current `GET /api/orders` already implements the user's order listing. I left the more advanced pieces—pagination, filtering, etc.—under future improvements rather than pretending they're already implemented.

Also, I kept **final role-based authorization** under planned work, matching where you currently are in the project.
```
