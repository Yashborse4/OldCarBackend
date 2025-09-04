#!/usr/bin/env python3
"""
Test script to verify API improvements for the Car Selling application.
This script tests the enhanced validation and error handling.
"""

import requests
import json
import time

# Configuration
BASE_URL = "http://localhost:8080/api/auth"
HEADERS = {"Content-Type": "application/json"}

def print_response(response, test_name):
    """Print formatted response for better readability"""
    print(f"\n{'='*60}")
    print(f"TEST: {test_name}")
    print(f"Status Code: {response.status_code}")
    print(f"Response Headers: {dict(response.headers)}")
    try:
        print(f"Response Body: {json.dumps(response.json(), indent=2)}")
    except:
        print(f"Response Body: {response.text}")
    print(f"{'='*60}")

def test_health_check():
    """Test health check endpoint"""
    try:
        response = requests.get(f"{BASE_URL}/health", headers=HEADERS)
        print_response(response, "Health Check")
        return response.status_code == 200
    except Exception as e:
        print(f"Health check failed: {e}")
        return False

def test_registration_validation():
    """Test registration with various validation scenarios"""
    tests = [
        {
            "name": "Valid Registration",
            "data": {
                "username": "testuser123",
                "email": "test@example.com",
                "password": "TestPass123!",
                "role": "VIEWER"
            },
            "expected_status": 201
        },
        {
            "name": "Username Already Exists",
            "data": {
                "username": "testuser123",  # Same username as above
                "email": "test2@example.com",
                "password": "TestPass123!",
                "role": "VIEWER"
            },
            "expected_status": 409
        },
        {
            "name": "Email Already Exists",
            "data": {
                "username": "testuser456",
                "email": "test@example.com",  # Same email as above
                "password": "TestPass123!",
                "role": "VIEWER"
            },
            "expected_status": 409
        },
        {
            "name": "Password Too Short",
            "data": {
                "username": "testuser789",
                "email": "test3@example.com",
                "password": "short",
                "role": "VIEWER"
            },
            "expected_status": 400
        },
        {
            "name": "Invalid Email Format",
            "data": {
                "username": "testuser789",
                "email": "invalid-email",
                "password": "TestPass123!",
                "role": "VIEWER"
            },
            "expected_status": 400
        },
        {
            "name": "Username Too Short",
            "data": {
                "username": "ab",
                "email": "test4@example.com",
                "password": "TestPass123!",
                "role": "VIEWER"
            },
            "expected_status": 400
        },
        {
            "name": "Invalid Username Characters",
            "data": {
                "username": "test-user@123",
                "email": "test5@example.com",
                "password": "TestPass123!",
                "role": "VIEWER"
            },
            "expected_status": 400
        },
        {
            "name": "Weak Password (No Special Char)",
            "data": {
                "username": "testuser789",
                "email": "test6@example.com",
                "password": "TestPass123",
                "role": "VIEWER"
            },
            "expected_status": 400
        }
    ]
    
    for test in tests:
        try:
            response = requests.post(f"{BASE_URL}/register", 
                                   headers=HEADERS, 
                                   data=json.dumps(test["data"]))
            print_response(response, test["name"])
            
            if response.status_code != test["expected_status"]:
                print(f"❌ Expected status {test['expected_status']}, got {response.status_code}")
            else:
                print(f"✅ Test passed")
                
        except Exception as e:
            print(f"❌ Test failed: {e}")

def test_login_validation():
    """Test login with various validation scenarios"""
    tests = [
        {
            "name": "Valid Login",
            "data": {
                "usernameOrEmail": "testuser123",
                "password": "TestPass123!"
            },
            "expected_status": 200
        },
        {
            "name": "Invalid Password",
            "data": {
                "usernameOrEmail": "testuser123",
                "password": "WrongPassword123!"
            },
            "expected_status": 401
        },
        {
            "name": "Non-existent User",
            "data": {
                "usernameOrEmail": "nonexistentuser",
                "password": "TestPass123!"
            },
            "expected_status": 401
        },
        {
            "name": "Empty Username",
            "data": {
                "usernameOrEmail": "",
                "password": "TestPass123!"
            },
            "expected_status": 400
        },
        {
            "name": "Empty Password",
            "data": {
                "usernameOrEmail": "testuser123",
                "password": ""
            },
            "expected_status": 400
        }
    ]
    
    for test in tests:
        try:
            response = requests.post(f"{BASE_URL}/login", 
                                   headers=HEADERS, 
                                   data=json.dumps(test["data"]))
            print_response(response, test["name"])
            
            if response.status_code != test["expected_status"]:
                print(f"❌ Expected status {test['expected_status']}, got {response.status_code}")
            else:
                print(f"✅ Test passed")
                
        except Exception as e:
            print(f"❌ Test failed: {e}")

def test_forgot_password_validation():
    """Test forgot password with various validation scenarios"""
    tests = [
        {
            "name": "Valid Forgot Password Request",
            "data": {
                "username": "testuser123"
            },
            "expected_status": 200
        },
        {
            "name": "Non-existent User",
            "data": {
                "username": "nonexistentuser"
            },
            "expected_status": 500  # This will throw RuntimeException
        },
        {
            "name": "Empty Username",
            "data": {
                "username": ""
            },
            "expected_status": 400
        }
    ]
    
    for test in tests:
        try:
            response = requests.post(f"{BASE_URL}/forgot-password", 
                                   headers=HEADERS, 
                                   data=json.dumps(test["data"]))
            print_response(response, test["name"])
            
            if response.status_code != test["expected_status"]:
                print(f"❌ Expected status {test['expected_status']}, got {response.status_code}")
            else:
                print(f"✅ Test passed")
                
        except Exception as e:
            print(f"❌ Test failed: {e}")

def test_reset_password_validation():
    """Test reset password with various validation scenarios"""
    tests = [
        {
            "name": "Valid Reset Password",
            "data": {
                "username": "testuser123",
                "otp": "12345",
                "newPassword": "NewTestPass123!"
            },
            "expected_status": 200
        },
        {
            "name": "Invalid OTP",
            "data": {
                "username": "testuser123",
                "otp": "99999",
                "newPassword": "NewTestPass123!"
            },
            "expected_status": 400
        },
        {
            "name": "Weak New Password",
            "data": {
                "username": "testuser123",
                "otp": "12345",
                "newPassword": "weak"
            },
            "expected_status": 400
        },
        {
            "name": "Invalid OTP Format",
            "data": {
                "username": "testuser123",
                "otp": "abc123",
                "newPassword": "NewTestPass123!"
            },
            "expected_status": 400
        }
    ]
    
    for test in tests:
        try:
            response = requests.post(f"{BASE_URL}/reset-password", 
                                   headers=HEADERS, 
                                   data=json.dumps(test["data"]))
            print_response(response, test["name"])
            
            if response.status_code != test["expected_status"]:
                print(f"❌ Expected status {test['expected_status']}, got {response.status_code}")
            else:
                print(f"✅ Test passed")
                
        except Exception as e:
            print(f"❌ Test failed: {e}")

def main():
    """Main test function"""
    print("🚀 Starting API Improvement Tests")
    print("Make sure your Spring Boot application is running on localhost:8080")
    
    # Test health check first
    if not test_health_check():
        print("❌ Health check failed. Make sure the application is running.")
        return
    
    print("\n📝 Testing Registration Validation...")
    test_registration_validation()
    
    print("\n🔐 Testing Login Validation...")
    test_login_validation()
    
    print("\n🔑 Testing Forgot Password Validation...")
    test_forgot_password_validation()
    
    print("\n🔄 Testing Reset Password Validation...")
    test_reset_password_validation()
    
    print("\n✅ All tests completed!")

if __name__ == "__main__":
    main() 