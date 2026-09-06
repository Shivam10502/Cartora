````markdown
# Cartora 🛒

Cartora is an e-commerce backend application built using Spring Boot.

The project is currently under active development.

---

## 🚀 Current Features

### 🔐 Authentication & Security

- User registration
- User login
- Password encryption using BCrypt
- JWT-based authentication
- Stateless authentication using Spring Security
- Role support:
  - `CUSTOMER`
  - `ADMIN`
- Global exception handling
- Custom authentication entry point

---

### 📂 Category Management

- Create category
- Get category by ID
- Get all categories
- Update category
- Delete category
- Duplicate category name validation
- Category not found handling
- Request validation

---

### 📦 Product Management

- Create product
- Get product by ID
- Get all products
- Update product
- Delete product
- Product SKU uniqueness
- Product-to-category relationship
- Product price validation
- Product stock validation
- Category existence validation
- Product not found handling

---

### 🛒 Cart Management

- Add product to cart
- View current user's cart
- Update cart item quantity
- Remove individual cart item
- Clear entire cart
- Cart ownership validation
- Product stock validation while adding/updating
- Cart item uniqueness per product
- Automatic cart creation when the first product is added
- Cart and cart item DTOs
- Item subtotal calculation
- Transaction management using `@Transactional`

### Cart Relationships

```text
User
  │
  │ 1 : 1
  ▼
Cart
  │
  │ 1 : N
  ▼
CartItem
  │
  │ N : 1
  ▼
Product
````

---

## 🛠️ Tech Stack

* Java 21
* Spring Boot
* Spring Security
* JWT
* Spring Data JPA
* Hibernate
* PostgreSQL
* Jakarta Validation
* Lombok
* Maven
* Postman

---

## 📁 Project Structure

```text
src
└── main
    └── java
        └── com.ecom.cartora
            │
            ├── auth
            │
            ├── cart
            │   ├── dto
            │   ├── Cart.java
            │   ├── CartItem.java
            │   ├── CartRepo.java
            │   ├── CartItemRepo.java
            │   ├── CartService.java
            │   └── CartController.java
            │
            ├── category
            │   ├── dto
            │   ├── Category.java
            │   ├── CategoryRepo.java
            │   ├── CategoryService.java
            │   └── CategoryController.java
            │
            ├── product
            │   ├── dto
            │   ├── Product.java
            │   ├── ProductRepo.java
            │   ├── ProductService.java
            │   └── ProductController.java
            │
            ├── security
            │   ├── JwtFilter.java
            │   ├── JwtService.java
            │   ├── SecurityConfig.java
            │   ├── CustomUserDetailsService.java
            │   └── CustomAuthenticationEntryPoint.java
            │
            ├── user
            │   ├── User.java
            │   ├── UserRepo.java
            │   └── ...
            │
            └── exception
                ├── GlobalExceptionHandler.java
                └── custom exceptions
```

---

## 🔑 Authentication Flow

```text
User
 │
 │ Register
 ▼
User stored in PostgreSQL
 │
 │ Login
 ▼
AuthenticationManager
 │
 ▼
UserDetailsService
 │
 ▼
Password verification using BCrypt
 │
 ▼
JWT generated
 │
 ▼
Client receives JWT
 │
 │ Authorization: Bearer <JWT>
 ▼
JwtFilter
 │
 ▼
JWT validation
 │
 ▼
SecurityContext
 │
 ▼
Controller
```

The application uses stateless sessions:

```text
SessionCreationPolicy.STATELESS
```

---

## 🗄️ Database Relationships

### User → Cart

```text
User 1 ───── 1 Cart
```

Each user can have one cart.

### Cart → CartItem

```text
Cart 1 ───── * CartItem
```

A cart can contain multiple cart items.

### CartItem → Product

```text
CartItem * ───── 1 Product
```

Each cart item references one product.

A unique constraint on:

```text
(cart_id, product_id)
```

prevents the same product from appearing multiple times in the same cart.

---

# 🌐 API Endpoints

## Authentication

```http
POST /api/users/register
POST /api/auth/login
```

---

## Category APIs

```http
POST   /api/categories
GET    /api/categories
GET    /api/categories/{id}
PUT    /api/categories/{id}
DELETE /api/categories/{id}
```

---

## Product APIs

```http
POST   /api/products
GET    /api/products
GET    /api/products/{id}
PUT    /api/products/{id}
DELETE /api/products/{id}
```

---

## 🛒 Cart APIs

### Add Product to Cart

```http
POST /api/cart/items
```

```json
{
  "productId": 1,
  "quantity": 2
}
```

### Get Current User's Cart

```http
GET /api/cart
```

### Update Cart Item Quantity

```http
PUT /api/cart/items/{cartItemId}
```

```json
{
  "quantity": 5
}
```

### Remove Cart Item

```http
DELETE /api/cart/items/{cartItemId}
```

### Clear Cart

```http
DELETE /api/cart
```

---

## 📦 Product Stock & Cart Behavior

Adding a product to the cart does **not** reduce the product's stock.

```text
Product stock = 10

Customer adds 3 products to cart

Product stock = 10
Cart quantity = 3
```

The cart represents the customer's intended purchase.

When adding or updating cart quantities, the application checks that the requested quantity does not exceed the available product stock.

Stock deduction will be handled during the order/checkout process.

---

## 🧾 Cart Response Example

```json
{
  "cartId": 1,
  "userId": 2,
  "items": [
    {
      "cartItemId": 1,
      "productId": 1,
      "productName": "Wireless Mouse",
      "price": 799.00,
      "quantity": 2,
      "subtotal": 1598.00
    }
  ]
}
```

---

## ✅ Validation & Exception Handling

The application uses Jakarta Bean Validation.

Examples:

```text
@NotBlank
@NotNull
@Positive
@PositiveOrZero
@Size
```

Custom exceptions include:

* Category already exists
* Category not found
* Product not found
* Insufficient stock
* Cart not found
* Cart item not found

A global exception handler provides consistent error responses.

---

## 🧪 API Testing

Postman is being used to test the APIs.

Main testing flow:

```text
Register User
      ↓
Login
      ↓
Receive JWT
      ↓
Create Category
      ↓
Create Product
      ↓
Add Product to Cart
      ↓
View Cart
      ↓
Update Cart Quantity
      ↓
Remove Cart Item
      ↓
Clear Cart
```

Cart test cases include:

* Adding a product to an empty cart
* Adding the same product multiple times
* Updating quantity
* Quantity exceeding available stock
* Invalid quantity
* Removing a cart item
* Clearing the cart
* Preventing a user from modifying another user's cart item

---

## ⚙️ Running the Application

### Prerequisites

* Java 21
* Maven
* PostgreSQL

### Database Configuration

Configure PostgreSQL in:

```text
src/main/resources/application.properties
```

Example:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/cartora
spring.datasource.username=YOUR_USERNAME
spring.datasource.password=YOUR_PASSWORD

spring.jpa.hibernate.ddl-auto=update
```

### Run the Application

Linux/macOS:

```bash
./mvnw spring-boot:run
```

Windows:

```bash
mvnw.cmd spring-boot:run
```

Application URL:

```text
http://localhost:8080
```

---

## 🔐 Security

Cartora uses:

* BCrypt password hashing
* JWT authentication
* Stateless sessions
* Spring Security
* JWT authentication filter
* Role-based user model
* Custom authentication entry point

Sensitive information such as database passwords and JWT secrets should not be committed to the repository.

---

## 📌 Development Roadmap

### Completed

* [x] Project setup
* [x] PostgreSQL configuration
* [x] User registration
* [x] User login
* [x] BCrypt password encryption
* [x] JWT authentication
* [x] Spring Security configuration
* [x] Global exception handling
* [x] Category CRUD
* [x] Product CRUD
* [x] Cart creation
* [x] Add product to cart
* [x] Get cart
* [x] Update cart item quantity
* [x] Remove cart item
* [x] Clear cart
* [x] Cart DTOs
* [x] Cart ownership validation
* [x] Stock validation for cart operations

### Upcoming

* [ ] Final role-based authorization
* [ ] Order management
* [ ] Checkout flow
* [ ] Stock deduction during order creation
* [ ] Payment integration
* [ ] Order history
* [ ] User profile management
* [ ] Address management
* [ ] Pagination and filtering
* [ ] Advanced product search
* [ ] Unit tests
* [ ] Integration tests
* [ ] API documentation
* [ ] Production security hardening
* [ ] Dockerization
* [ ] Deployment

---

## 🎯 Project Goal

The goal of Cartora is to build a production-style e-commerce backend while following clean backend development practices.

The project focuses on:

* RESTful API design
* Layered architecture
* DTO-based API communication
* Database relationships
* Authentication and authorization
* Exception handling
* Request validation
* Transaction management
* Secure API design
* Maintainable code structure
* API testing using Postman

---

## 📈 Project Status

**🚧 In Development**

Current implementation:

```text
Authentication
      +
Category Management
      +
Product Management
      +
Cart Management
```

### Next Major Module

**Order Management** 🧾

```
```
