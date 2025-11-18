# Cloud File Retrieval Engine (AWS Lambda + DynamoDB)

This project implements a fully cloud-based **File Retrieval Engine (FRE)** using **AWS Lambda**, **Amazon DynamoDB**, and a **Java client application**.  
It demonstrates a scalable architecture for document indexing and term-based search using cloud-native services.

---

## 🔹 Architecture Overview

The system is composed of three main components:

### **1. Java Client (Local)**
- Provides interactive commands (`register`, `index-file`, `search-json`, `quit`)
- Invokes AWS Lambda functions via the AWS SDK
- Reads `.txt` documents locally for indexing

### **2. AWS Lambda (Processing Layer)**
Three Java-based Lambda functions:

| Function Name | Purpose |
|---------------|---------|
| `RegisterHandler` | Generates a unique client ID |
| `ComputeIndexHandler` | Tokenizes document text and updates DynamoDB indexes |
| `ComputeSearchHandler` | Performs term-based ranked search using DynamoDB |

### **3. Amazon DynamoDB (Data Layer)**
Three tables are used:

| Table Name | Purpose |
|------------|---------|
| `FRE_DocumentMap` | Maps `docId` → document path |
| `FRE_TermIndex` | Stores term postings: `(term, docId, tf)` |
| `FRE_Counters` | Stores counters such as `docSeq` for generating new document IDs |

The architecture enables a separation between the **client**, **processing**, and **data layers**, allowing indexing and searching to be performed entirely in the cloud.

---

## 🔹 Features

### ✔ Client-side text parsing  
Documents are read locally and sent to Lambda as term-frequency maps.

### ✔ Cloud-based indexing  
Term frequencies are uploaded and stored in DynamoDB using `ComputeIndexHandler`.

### ✔ Ranked keyword search  
Search queries retrieve matching documents sorted by relevance scores.

### ✔ Modular Maven project  
Organized into:
- `client/`
- `core/`
- `lambda/`

### ✔ Fully serverless  
No servers, sockets, or local backend required.

---

## 🔹 Repository Structure

File-Retrieval-Engine-Cloud-Project/
│
├── client/
│ └── src/main/java/client/App.java
│
├── core/
│ └── src/main/java/core/TextTokenizer.java
│
├── lambda/
│ └── src/main/java/lambda/*.java
│
├── folder/
│ └── book1.txt (example document)
│
├── pom.xml (root build file)
├── .gitignore
└── README.md

---

## 🔹 Build Instructions

### **Prerequisites**
- Java **21**
- Maven
- AWS CLI configured with valid IAM credentials
- AWS Lambda execution role with DynamoDB permissions

### **Build the Full Project**
```bash
mvn -q clean install


🔹Deploying AWS Lambda Functions:

1. Package Lambda Code
mvn -q -pl lambda -am package

2. Locate the shaded JAR
JAR=$(ls lambda/target/*-shaded.jar)

3. Deploy Functions

Register Handler:
aws lambda create-function \
  --function-name RegisterHandler \
  --runtime java21 \
  --role <LAMBDA_ROLE_ARN> \
  --handler lambda.RegisterHandler::handleRequest \
  --zip-file fileb://$JAR

Index Handler:
aws lambda create-function \
  --function-name ComputeIndexHandler \
  --runtime java21 \
  --role <LAMBDA_ROLE_ARN> \
  --handler lambda.ComputeIndexHandler::handleRequest \
  --environment "Variables={TABLE_DOCMAP=FRE_DocumentMap,TABLE_TERMIDX=FRE_TermIndex,TABLE_COUNTERS=FRE_Counters}" \
  --zip-file fileb://$JAR

Search Handler:
aws lambda create-function \
  --function-name ComputeSearchHandler \
  --runtime java21 \
  --role <LAMBDA_ROLE_ARN> \
  --handler lambda.ComputeSearchHandler::handleRequest \
  --environment "Variables={TABLE_DOCMAP=FRE_DocumentMap,TABLE_TERMIDX=FRE_TermIndex,TABLE_COUNTERS=FRE_Counters}" \
  --zip-file fileb://$JAR

Update Lambda Code:
aws lambda update-function-code \
  --function-name ComputeSearchHandler \
  --zip-file fileb://$JAR


🔹DynamoDB Table Setup:

Create Tables:

aws dynamodb create-table \
  --table-name FRE_DocumentMap \
  --attribute-definitions AttributeName=docId,AttributeType=N \
  --key-schema AttributeName=docId,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST

aws dynamodb create-table \
  --table-name FRE_TermIndex \
  --attribute-definitions AttributeName=term,AttributeType=S AttributeName=docId,AttributeType=N \
  --key-schema AttributeName=term,KeyType=HASH AttributeName=docId,KeyType=RANGE \
  --billing-mode PAY_PER_REQUEST

aws dynamodb create-table \
  --table-name FRE_Counters \
  --attribute-definitions AttributeName=name,AttributeType=S \
  --key-schema AttributeName=name,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST

Initialize Counter:
aws dynamodb put-item \
  --table-name FRE_Counters \
  --item '{"name":{"S":"docSeq"},"value":{"N":"0"}}'


🔹Running the Java Client:
Start client
mvn -q -pl client org.codehaus.mojo:exec-maven-plugin:3.1.0:java -Dexec.mainClass=client.App

Supported Commands

Command	      Description
register	    Starts a new cloud session
index-file    <path>	Uploads document term frequencies to Lambda
search-json   <json>	Searches for the provided terms
pwd	          Shows working directory
quit	        Exit program


🔹Example Session:
> register
{"clientId":"56186d67-dab3-4c2e-b4ac-15f4f4b3f4b7"}

> index-file folder/book1.txt
{"indexed":"folder/book1.txt","status":"OK","docId":93}

> search-json {"terms":["the"]}
{"count":92,"results":[...]}

> search-json {"terms":["child"]}
{"count":44,"results":[...]}

> quit
Bye.

This demonstrates:

successful Lambda invocation

stored postings in DynamoDB

ranked search results

Notes

The project uses small local files for fast Lambda execution.

The architecture supports large datasets with additional scaling optimizations.

The design is fully serverless, reducing operational overhead.


🔹Conclusion:

This project delivers a cloud-native File Retrieval Engine built with AWS Lambda and DynamoDB.
It showcases a clean separation of concerns, scalable data storage, and fully serverless document indexing and retrieval.
