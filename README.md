# ☁️ Cloud File Retrieval Engine (AWS Lambda + DynamoDB)

This project implements a fully cloud-based **File Retrieval Engine (FRE)** using  
**AWS Lambda**, **Amazon DynamoDB**, and a **Java client application**.  
It provides serverless document indexing and ranked keyword search.

---

## 📌 Architecture Overview

### **1. Client (Local Java Application)**
- Runs interactively on the user's machine
- Commands:
  - `register`
  - `index-file <path>`
  - `index-json <json>`
  - `search-json <json>`
  - `pwd`
  - `quit`
- Communicates with AWS Lambda using AWS SDK

---

### **2. AWS Lambda (Processing Layer)**  
Three Java Lambda functions handle all document processing:

| Lambda Function | Purpose |
|-----------------|---------|
| `RegisterHandler` | Generates unique client IDs |
| `ComputeIndexHandler` | Tokenizes documents and stores term frequencies in DynamoDB |
| `ComputeSearchHandler` | Performs ranked search using stored postings |

---

### **3. DynamoDB (Data Layer)**  
Three NoSQL tables store document metadata and term indexes:

| Table | Purpose |
|-------|---------|
| **FRE_DocumentMap** | Maps docId → document path |
| **FRE_TermIndex** | Stores each term’s postings list |
| **FRE_Counters** | Stores auto-increment counter (`docSeq`) |

---

## 📦 Repository Structure

```
File-Retrieval-Engine-Cloud-Project/
│
├── client/
│   └── src/main/java/client/App.java
│   └── pom.xml
│
├── core/
│   └── src/main/java/core/TextTokenizer.java
│   └── src/main/java/core/IndexStore.java
│   └── pom.xml
│
├── lambda/
│   └── src/main/java/lambda/RegisterHandler.java
│   └── src/main/java/lambda/ComputeIndexHandler.java
│   └── src/main/java/lambda/ComputeSearchHandler.java
│   └── pom.xml
│
├── folder/
│   └── book1.txt   (example dataset file)
│
├── .gitignore
├── pom.xml
└── README.md
```

---

## 🛠 Requirements
- **Java 21**
- **Maven**
- **AWS CLI**
- AWS IAM role with:
  - `lambda:InvokeFunction`
  - DynamoDB read/write permissions

---

# 🚀 1. Build Instructions

### Build the Entire Project
```bash
mvn -q clean install
```

---

# ⚙️ 2. AWS Lambda Deployment

### Step 1 — Build Lambda JAR
```bash
mvn -q -pl lambda -am package
```

### Step 2 — Store JAR Path
```bash
JAR=$(ls lambda/target/*-shaded.jar)
```

---

## Step 3 — Deploy Lambda Functions

### Register Handler
```bash
aws lambda create-function \
  --function-name RegisterHandler \
  --runtime java21 \
  --role <LAMBDA_ROLE_ARN> \
  --handler lambda.RegisterHandler::handleRequest \
  --zip-file fileb://$JAR
```

### ComputeIndexHandler
```bash
aws lambda create-function \
  --function-name ComputeIndexHandler \
  --runtime java21 \
  --role <LAMBDA_ROLE_ARN> \
  --handler lambda.ComputeIndexHandler::handleRequest \
  --environment "Variables={TABLE_DOCMAP=FRE_DocumentMap,TABLE_TERMIDX=FRE_TermIndex,TABLE_COUNTERS=FRE_Counters}" \
  --zip-file fileb://$JAR
```

### ComputeSearchHandler
```bash
aws lambda create-function \
  --function-name ComputeSearchHandler \
  --runtime java21 \
  --role <LAMBDA_ROLE_ARN> \
  --handler lambda.ComputeSearchHandler::handleRequest \
  --environment "Variables={TABLE_DOCMAP=FRE_DocumentMap,TABLE_TERMIDX=FRE_TermIndex,TABLE_COUNTERS=FRE_Counters}" \
  --zip-file fileb://$JAR
```

### Update Lambda Code Later
```bash
aws lambda update-function-code \
  --function-name ComputeSearchHandler \
  --zip-file fileb://$JAR
```

---

# 🗄️ 3. DynamoDB Setup

### Create FRE_DocumentMap
```bash
aws dynamodb create-table \
  --table-name FRE_DocumentMap \
  --attribute-definitions AttributeName=docId,AttributeType=N \
  --key-schema AttributeName=docId,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST
```

### Create FRE_TermIndex
```bash
aws dynamodb create-table \
  --table-name FRE_TermIndex \
  --attribute-definitions AttributeName=term,AttributeType=S AttributeName=docId,AttributeType=N \
  --key-schema AttributeName=term,KeyType=HASH AttributeName=docId,KeyType=RANGE \
  --billing-mode PAY_PER_REQUEST
```

### Create FRE_Counters
```bash
aws dynamodb create-table \
  --table-name FRE_Counters \
  --attribute-definitions AttributeName=name,AttributeType=S \
  --key-schema AttributeName=name,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST
```

### Initialize Counter
```bash
aws dynamodb put-item \
  --table-name FRE_Counters \
  --item '{"name":{"S":"docSeq"},"value":{"N":"0"}}'
```

---

# 🖥️ 4. Running the Java Client

### Start the Client
```bash
mvn -q -pl client org.codehaus.mojo:exec-maven-plugin:3.1.0:java -Dexec.mainClass=client.App
```

---

# 🧪 5. Example Session

```
> register
{"clientId":"56186d67-dab3-4c2e-b4ac-15f4f4b3f4b7"}

> index-file folder/book1.txt
{"indexed":"folder/book1.txt","status":"OK","docId":93}

> search-json {"terms":["the"]}
{"count":92,"results":[ ... ]}

> search-json {"terms":["child"]}
{"count":44,"results":[ ... ]}

> quit
Bye.
```

---

# 🧹 6. .gitignore (Dataset Not Included)

```
# Build artifacts
target/
**/target/
*.class
*.log
*.out

# IDE / OS
.vscode/
.idea/
.DS_Store

# Envs / creds
.venv/
.env
.aws/

# Datasets (keep repo small)
dataset/
*.zip
```

---

# 🏁 Conclusion

This system demonstrates a fully serverless **document indexing and retrieval engine** using:
- AWS Lambda  
- DynamoDB  
- Java client  
- Maven build system  

The system successfully indexes files, stores them in DynamoDB, and performs ranked keyword search.

---


