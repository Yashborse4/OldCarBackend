# 🚀 Frontend Integration Guide - Car Marketplace API

A comprehensive guide for integrating the Car Marketplace backend APIs with React Native frontend applications.

## 📋 Table of Contents

- [Overview](#overview)
- [API Base Configuration](#api-base-configuration)
- [Authentication Integration](#authentication-integration)
- [Vehicle Management Integration](#vehicle-management-integration)
- [Chat System Integration](#chat-system-integration)
- [WebSocket Integration](#websocket-integration)
- [File Upload Integration](#file-upload-integration)
- [Error Handling](#error-handling)
- [State Management](#state-management)
- [React Native Specific Examples](#react-native-specific-examples)

## 🏗 Overview

This guide provides specific implementation examples for integrating with the Car Marketplace backend APIs. The backend provides REST APIs and WebSocket support for real-time features.

### Backend Architecture
- **Base URL**: `https://api.carworld.com` (Production) / `http://localhost:8080` (Development)
- **Authentication**: JWT with refresh tokens
- **Real-time**: WebSocket with STOMP protocol
- **File Storage**: AWS S3 integration
- **Database**: MySQL with comprehensive indexing

---

## 🔧 API Base Configuration

### 1. Environment Setup

Create environment configuration files:

**config/api.js**
```javascript
const API_CONFIG = {
  development: {
    BASE_URL: 'http://localhost:8080',
    WS_URL: 'ws://localhost:8080/ws',
    TIMEOUT: 30000,
  },
  production: {
    BASE_URL: 'https://api.carworld.com',
    WS_URL: 'wss://api.carworld.com/ws',
    TIMEOUT: 10000,
  }
};

export const getAPIConfig = () => {
  return __DEV__ ? API_CONFIG.development : API_CONFIG.production;
};
```

### 2. Axios Configuration

**services/apiClient.js**
```javascript
import axios from 'axios';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { getAPIConfig } from '../config/api';

const { BASE_URL, TIMEOUT } = getAPIConfig();

// Create axios instance
const apiClient = axios.create({
  baseURL: BASE_URL,
  timeout: TIMEOUT,
  headers: {
    'Content-Type': 'application/json',
    'Accept': 'application/json',
  },
});

// Request interceptor to add auth token
apiClient.interceptors.request.use(
  async (config) => {
    const token = await AsyncStorage.getItem('accessToken');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Response interceptor for token refresh
apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;

    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true;
      
      try {
        const refreshToken = await AsyncStorage.getItem('refreshToken');
        if (refreshToken) {
          const response = await refreshAccessToken(refreshToken);
          const { accessToken } = response.data.data;
          
          await AsyncStorage.setItem('accessToken', accessToken);
          originalRequest.headers.Authorization = `Bearer ${accessToken}`;
          
          return apiClient(originalRequest);
        }
      } catch (refreshError) {
        // Redirect to login
        await AsyncStorage.multiRemove(['accessToken', 'refreshToken']);
        // Navigate to login screen
      }
    }

    return Promise.reject(error);
  }
);

const refreshAccessToken = async (refreshToken) => {
  return axios.post(`${BASE_URL}/api/auth/refresh-token`, {
    refreshToken,
  });
};

export default apiClient;
```

---

## 🔐 Authentication Integration

### 1. Authentication Service

**services/authService.js**
```javascript
import apiClient from './apiClient';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { Platform } from 'react-native';
import DeviceInfo from 'react-native-device-info';

class AuthService {
  
  // User Registration
  async register(userData) {
    try {
      const response = await apiClient.post('/api/auth/register', {
        username: userData.username,
        email: userData.email,
        password: userData.password,
        role: userData.role || 'VIEWER',
      });

      return {
        success: true,
        data: response.data.data,
        message: response.data.message,
      };
    } catch (error) {
      return this.handleError(error);
    }
  }

  // User Login with Device Info
  async login(credentials) {
    try {
      const deviceInfo = await this.getDeviceInfo();
      
      const response = await apiClient.post('/api/auth/login', {
        usernameOrEmail: credentials.usernameOrEmail,
        password: credentials.password,
        deviceInfo,
      });

      const { data } = response.data;
      
      // Store tokens
      await AsyncStorage.multiSet([
        ['accessToken', data.accessToken],
        ['refreshToken', data.refreshToken],
        ['userInfo', JSON.stringify({
          userId: data.userId,
          username: data.username,
          email: data.email,
          role: data.role,
          location: data.location,
        })],
      ]);

      return {
        success: true,
        data,
        message: response.data.message,
      };
    } catch (error) {
      return this.handleError(error);
    }
  }

  // Get Device Info
  async getDeviceInfo() {
    try {
      return {
        platform: Platform.OS,
        version: Platform.Version.toString(),
        deviceId: await DeviceInfo.getUniqueId(),
        appVersion: DeviceInfo.getVersion(),
        deviceModel: await DeviceInfo.getModel(),
        locale: await DeviceInfo.getLocales()[0]?.languageTag,
      };
    } catch (error) {
      return {
        platform: Platform.OS,
        version: 'unknown',
        deviceId: 'unknown',
      };
    }
  }

  // Token Validation
  async validateToken() {
    try {
      const response = await apiClient.post('/api/auth/validate-token');
      return {
        success: true,
        data: response.data.data,
      };
    } catch (error) {
      return this.handleError(error);
    }
  }

  // Forgot Password
  async forgotPassword(username) {
    try {
      const response = await apiClient.post('/api/auth/forgot-password', {
        username,
      });

      return {
        success: true,
        message: response.data.message,
      };
    } catch (error) {
      return this.handleError(error);
    }
  }

  // Reset Password
  async resetPassword(resetData) {
    try {
      const response = await apiClient.post('/api/auth/reset-password', {
        username: resetData.username,
        otp: resetData.otp,
        newPassword: resetData.newPassword,
      });

      return {
        success: true,
        message: response.data.message,
      };
    } catch (error) {
      return this.handleError(error);
    }
  }

  // Logout
  async logout() {
    try {
      await apiClient.post('/api/auth/logout');
      await AsyncStorage.multiRemove(['accessToken', 'refreshToken', 'userInfo']);
      
      return {
        success: true,
        message: 'Logged out successfully',
      };
    } catch (error) {
      // Clear storage even if API call fails
      await AsyncStorage.multiRemove(['accessToken', 'refreshToken', 'userInfo']);
      return {
        success: true,
        message: 'Logged out successfully',
      };
    }
  }

  // Check Username Availability
  async checkUsernameAvailability(username) {
    try {
      const response = await apiClient.get(`/api/auth/check-username/${username}`);
      return {
        success: true,
        available: response.data.data.available,
      };
    } catch (error) {
      return this.handleError(error);
    }
  }

  // Check Email Availability
  async checkEmailAvailability(email) {
    try {
      const response = await apiClient.get(`/api/auth/check-email/${email}`);
      return {
        success: true,
        available: response.data.data.available,
      };
    } catch (error) {
      return this.handleError(error);
    }
  }

  // Get Current User Info
  async getCurrentUser() {
    try {
      const userInfo = await AsyncStorage.getItem('userInfo');
      return userInfo ? JSON.parse(userInfo) : null;
    } catch (error) {
      return null;
    }
  }

  // Error Handler
  handleError(error) {
    const message = error.response?.data?.message || 
                   error.response?.data?.details || 
                   error.message || 
                   'An unexpected error occurred';

    return {
      success: false,
      message,
      errors: error.response?.data?.fieldErrors,
    };
  }
}

export default new AuthService();
```

### 2. React Native Authentication Hook

**hooks/useAuth.js**
```javascript
import { useState, useEffect, useContext, createContext } from 'react';
import authService from '../services/authService';

const AuthContext = createContext();

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isAuthenticated, setIsAuthenticated] = useState(false);

  useEffect(() => {
    checkAuthStatus();
  }, []);

  const checkAuthStatus = async () => {
    try {
      setIsLoading(true);
      const userInfo = await authService.getCurrentUser();
      
      if (userInfo) {
        // Validate token
        const validation = await authService.validateToken();
        if (validation.success) {
          setUser(userInfo);
          setIsAuthenticated(true);
        } else {
          await authService.logout();
        }
      }
    } catch (error) {
      console.error('Auth check failed:', error);
    } finally {
      setIsLoading(false);
    }
  };

  const login = async (credentials) => {
    try {
      setIsLoading(true);
      const result = await authService.login(credentials);
      
      if (result.success) {
        const userInfo = await authService.getCurrentUser();
        setUser(userInfo);
        setIsAuthenticated(true);
      }
      
      return result;
    } catch (error) {
      return { success: false, message: error.message };
    } finally {
      setIsLoading(false);
    }
  };

  const logout = async () => {
    try {
      setIsLoading(true);
      await authService.logout();
      setUser(null);
      setIsAuthenticated(false);
    } catch (error) {
      console.error('Logout failed:', error);
    } finally {
      setIsLoading(false);
    }
  };

  const register = async (userData) => {
    try {
      setIsLoading(true);
      return await authService.register(userData);
    } catch (error) {
      return { success: false, message: error.message };
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        isLoading,
        isAuthenticated,
        login,
        logout,
        register,
        checkAuthStatus,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
```

---

## 🚗 Vehicle Management Integration

### 1. Vehicle Service

**services/vehicleService.js**
```javascript
import apiClient from './apiClient';

class VehicleService {
  
  // Get All Vehicles with Pagination
  async getAllVehicles(params = {}) {
    try {
      const {
        page = 0,
        size = 20,
        sort = 'createdAt,desc'
      } = params;

      const response = await apiClient.get('/api/v2/cars', {
        params: { page, size, sort }
      });

      return {
        success: true,
        data: response.data.data,
      };
    } catch (error) {
      return this.handleError(error);
    }
  }

  // Get Vehicle by ID
  async getVehicleById(id) {
    try {
      const response = await apiClient.get(`/api/cars/${id}`);
      return {
        success: true,
        data: response.data.data,
      };
    } catch (error) {
      return this.handleError(error);
    }
  }

  // Search Vehicles with Advanced Filters
  async searchVehicles(searchCriteria, pagination = {}) {
    try {
      const params = {
        ...searchCriteria,
        page: pagination.page || 0,
        size: pagination.size || 20,
        sort: pagination.sort || 'createdAt,desc',
      };

      const response = await apiClient.get('/api/v2/cars/search', { params });
      
      return {
        success: true,
        data: response.data.data,
      };
    } catch (error) {
      return this.handleError(error);
    }
  }

  // Create Vehicle
  async createVehicle(vehicleData) {
    try {
      const response = await apiClient.post('/api/v2/cars', vehicleData);
      
      return {
        success: true,
        data: response.data.data,
        message: response.data.message,
      };
    } catch (error) {
      return this.handleError(error);
    }
  }

  // Update Vehicle
  async updateVehicle(id, vehicleData) {
    try {
      const response = await apiClient.patch(`/api/v2/cars/${id}`, vehicleData);
      
      return {
        success: true,
        data: response.data.data,
        message: response.data.message,
      };
    } catch (error) {
      return this.handleError(error);
    }
  }

  // Delete Vehicle
  async deleteVehicle(id, hardDelete = false) {
    try {
      const response = await apiClient.delete(`/api/v2/cars/${id}`, {
        params: { hard: hardDelete }
      });
      
      return {
        success: true,
        message: response.data.message,
      };
    } catch (error) {
      return this.handleError(error);
    }
  }

  // Get Vehicle Analytics
  async getVehicleAnalytics(id) {
    try {
      const response = await apiClient.get(`/api/v2/cars/${id}/analytics`);
      
      return {
        success: true,
        data: response.data.data,
      };
    } catch (error) {
      return this.handleError(error);
    }
  }

  // Track Vehicle View
  async trackVehicleView(id) {
    try {
      const response = await apiClient.post(`/api/v2/cars/${id}/view`);
      
      return {
        success: true,
        message: response.data.message,
      };
    } catch (error) {
      return this.handleError(error);
    }
  }

  // Track Vehicle Share
  async trackVehicleShare(id, platform) {
    try {
      const response = await apiClient.post(`/api/v2/cars/${id}/share`, {
        platform
      });
      
      return {
        success: true,
        message: response.data.message,
      };
    } catch (error) {
      return this.handleError(error);
    }
  }

  // Get Similar Vehicles
  async getSimilarVehicles(id, limit = 5) {
    try {
      const response = await apiClient.get(`/api/v2/cars/${id}/similar`, {
        params: { limit }
      });
      
      return {
        success: true,
        data: response.data.data,
      };
    } catch (error) {
      return this.handleError(error);
    }
  }

  // Toggle Feature Vehicle
  async toggleFeatureVehicle(id, featured) {
    try {
      const response = await apiClient.post(`/api/v2/cars/${id}/feature`, null, {
        params: { featured }
      });
      
      return {
        success: true,
        data: response.data.data,
        message: response.data.message,
      };
    } catch (error) {
      return this.handleError(error);
    }
  }

  handleError(error) {
    const message = error.response?.data?.message || error.message || 'An error occurred';
    return {
      success: false,
      message,
      errors: error.response?.data?.fieldErrors,
    };
  }
}

export default new VehicleService();
```

### 2. React Native Vehicle Components

**components/VehicleCard.jsx**
```javascript
import React from 'react';
import { View, Text, Image, TouchableOpacity, StyleSheet } from 'react-native';
import Icon from 'react-native-vector-icons/MaterialIcons';

const VehicleCard = ({ vehicle, onPress, onShare, onFavorite }) => {
  const formatPrice = (price) => {
    return new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: 'INR',
      maximumFractionDigits: 0,
    }).format(price);
  };

  const getImageUri = () => {
    return vehicle.images && vehicle.images.length > 0
      ? { uri: vehicle.images[0] }
      : require('../assets/placeholder-car.png');
  };

  return (
    <TouchableOpacity style={styles.card} onPress={() => onPress(vehicle)}>
      <View style={styles.imageContainer}>
        <Image source={getImageUri()} style={styles.image} resizeMode="cover" />
        {vehicle.featured && (
          <View style={styles.featuredBadge}>
            <Text style={styles.featuredText}>Featured</Text>
          </View>
        )}
        <TouchableOpacity
          style={styles.favoriteButton}
          onPress={() => onFavorite && onFavorite(vehicle)}
        >
          <Icon name="favorite-border" size={20} color="#fff" />
        </TouchableOpacity>
      </View>

      <View style={styles.content}>
        <Text style={styles.title} numberOfLines={1}>
          {vehicle.year} {vehicle.make} {vehicle.model}
        </Text>
        
        <Text style={styles.price}>{formatPrice(vehicle.price)}</Text>
        
        <View style={styles.details}>
          <View style={styles.detailItem}>
            <Icon name="speed" size={14} color="#666" />
            <Text style={styles.detailText}>{vehicle.mileage?.toLocaleString()} km</Text>
          </View>
          
          <View style={styles.detailItem}>
            <Icon name="local-gas-station" size={14} color="#666" />
            <Text style={styles.detailText}>{vehicle.specifications?.fuelType}</Text>
          </View>
          
          <View style={styles.detailItem}>
            <Icon name="location-on" size={14} color="#666" />
            <Text style={styles.detailText}>{vehicle.location}</Text>
          </View>
        </View>

        <View style={styles.footer}>
          <Text style={styles.dealer}>{vehicle.dealerName}</Text>
          
          <View style={styles.actions}>
            <TouchableOpacity
              style={styles.actionButton}
              onPress={() => onShare && onShare(vehicle)}
            >
              <Icon name="share" size={18} color="#007AFF" />
            </TouchableOpacity>
            
            <View style={styles.stats}>
              <Icon name="visibility" size={14} color="#999" />
              <Text style={styles.statText}>{vehicle.views}</Text>
            </View>
          </View>
        </View>
      </View>
    </TouchableOpacity>
  );
};

const styles = StyleSheet.create({
  card: {
    backgroundColor: '#fff',
    borderRadius: 12,
    marginHorizontal: 16,
    marginVertical: 8,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 8,
    elevation: 4,
  },
  imageContainer: {
    position: 'relative',
    height: 200,
  },
  image: {
    width: '100%',
    height: '100%',
    borderTopLeftRadius: 12,
    borderTopRightRadius: 12,
  },
  featuredBadge: {
    position: 'absolute',
    top: 12,
    left: 12,
    backgroundColor: '#FF6B35',
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 12,
  },
  featuredText: {
    color: '#fff',
    fontSize: 12,
    fontWeight: '600',
  },
  favoriteButton: {
    position: 'absolute',
    top: 12,
    right: 12,
    backgroundColor: 'rgba(0,0,0,0.5)',
    borderRadius: 20,
    padding: 8,
  },
  content: {
    padding: 16,
  },
  title: {
    fontSize: 18,
    fontWeight: '600',
    color: '#1a1a1a',
    marginBottom: 4,
  },
  price: {
    fontSize: 20,
    fontWeight: '700',
    color: '#007AFF',
    marginBottom: 8,
  },
  details: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 12,
  },
  detailItem: {
    flexDirection: 'row',
    alignItems: 'center',
    flex: 1,
  },
  detailText: {
    marginLeft: 4,
    fontSize: 12,
    color: '#666',
  },
  footer: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  dealer: {
    fontSize: 14,
    color: '#333',
    fontWeight: '500',
  },
  actions: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  actionButton: {
    padding: 8,
    marginRight: 8,
  },
  stats: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  statText: {
    marginLeft: 4,
    fontSize: 12,
    color: '#999',
  },
});

export default VehicleCard;
```

---

## 💬 Chat System Integration

### 1. Chat Service

**services/chatService.js**
```javascript
import apiClient from './apiClient';

class ChatService {
  
  // Get User's Chats
  async getMyChats(params = {}) {
    try {
      const { page = 0, size = 20 } = params;
      
      const response = await apiClient.get('/api/chat/my-chats', {
        params: { page, size }
      });

      return {
        success: true,
        data: response.data.data,
      };
    } catch (error) {
      return this.handleError(error);
    }
  }

  // Get Chat Details
  async getChatDetails(chatId) {
    try {
      const response = await apiClient.get(`/api/chat/${chatId}`);
      
      return {
        success: true,
        data: response.data.data,
      };
    } catch (error) {
      return this.handleError(error);
    }
  }

  // Create Private Chat
  async createPrivateChat(otherUserId, name) {
    try {
      const response = await apiClient.post('/api/chat/private', {
        otherUserId,
        name,
      });

      return {
        success: true,
        data: response.data.data,
        message: response.data.message,
      };
    } catch (error) {
      return this.handleError(error);
    }
  }

  // Create Group Chat
  async createGroupChat(chatData) {
    try {
      const response = await apiClient.post('/api/chat/group', chatData);

      return {
        success: true,
        data: response.data.data,
        message: response.data.message,
      };
    } catch (error) {
      return this.handleError(error);
    }
  }

  // Create Car Inquiry Chat
  async createCarInquiryChat(carId, sellerId, message) {
    try {
      const response = await apiClient.post('/api/chat/car-inquiry', {
        carId,
        sellerId,
        message,
      });

      return {
        success: true,
        data: response.data.data,
        message: response.data.message,
      };
    } catch (error) {
      return this.handleError(error);
    }
  }

  // Get Messages in Chat
  async getChatMessages(chatId, params = {}) {
    try {
      const { page = 0, size = 50 } = params;
      
      const response = await apiClient.get(`/api/chat/${chatId}/messages`, {
        params: { page, size }
      });

      return {
        success: true,
        data: response.data.data,
      };
    } catch (error) {
      return this.handleError(error);
    }
  }

  // Send Message (REST fallback)
  async sendMessage(chatId, content, replyToId = null) {
    try {
      const response = await apiClient.post(`/api/chat/${chatId}/messages`, {
        content,
        messageType: 'TEXT',
        replyToId,
      });

      return {
        success: true,
        data: response.data.data,
        message: response.data.message,
      };
    } catch (error) {
      return this.handleError(error);
    }
  }

  // Edit Message
  async editMessage(messageId, content) {
    try {
      const response = await apiClient.put(`/api/chat/messages/${messageId}`, {
        content,
      });

      return {
        success: true,
        data: response.data.data,
        message: response.data.message,
      };
    } catch (error) {
      return this.handleError(error);
    }
  }

  // Delete Message
  async deleteMessage(messageId) {
    try {
      const response = await apiClient.delete(`/api/chat/messages/${messageId}`);

      return {
        success: true,
        message: response.data.message,
      };
    } catch (error) {
      return this.handleError(error);
    }
  }

  // Mark Messages as Read
  async markMessagesAsRead(chatId, messageIds) {
    try {
      const response = await apiClient.post(`/api/chat/${chatId}/mark-read`, {
        messageIds,
      });

      return {
        success: true,
        message: response.data.message,
      };
    } catch (error) {
      return this.handleError(error);
    }
  }

  // Get Unread Count
  async getUnreadCount() {
    try {
      const response = await apiClient.get('/api/chat/unread-count');
      
      return {
        success: true,
        data: response.data.data,
      };
    } catch (error) {
      return this.handleError(error);
    }
  }

  // Search Messages
  async searchMessages(chatId, query, params = {}) {
    try {
      const { page = 0, size = 20 } = params;
      
      const response = await apiClient.get(`/api/chat/${chatId}/messages/search`, {
        params: { query, page, size }
      });

      return {
        success: true,
        data: response.data.data,
      };
    } catch (error) {
      return this.handleError(error);
    }
  }

  // Upload File for Chat
  async uploadChatFile(file) {
    try {
      const formData = new FormData();
      formData.append('file', {
        uri: file.uri,
        type: file.type,
        name: file.fileName || 'file',
      });

      const response = await apiClient.post('/api/chat/upload', formData, {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      });

      return {
        success: true,
        data: response.data.data,
      };
    } catch (error) {
      return this.handleError(error);
    }
  }

  handleError(error) {
    const message = error.response?.data?.message || error.message || 'An error occurred';
    return {
      success: false,
      message,
      errors: error.response?.data?.fieldErrors,
    };
  }
}

export default new ChatService();
```

---

## 🔌 WebSocket Integration

### 1. WebSocket Service

**services/websocketService.js**
```javascript
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { getAPIConfig } from '../config/api';

class WebSocketService {
  constructor() {
    this.client = null;
    this.isConnected = false;
    this.subscriptions = new Map();
    this.messageHandlers = new Map();
    this.reconnectAttempts = 0;
    this.maxReconnectAttempts = 5;
  }

  // Initialize WebSocket Connection
  async connect() {
    try {
      const token = await AsyncStorage.getItem('accessToken');
      if (!token) {
        throw new Error('No access token available');
      }

      const { WS_URL } = getAPIConfig();
      
      this.client = new Client({
        webSocketFactory: () => new SockJS(WS_URL),
        connectHeaders: {
          'Authorization': `Bearer ${token}`,
          'User-Agent': 'CarWorld-ReactNative-WebSocket',
        },
        debug: __DEV__ ? console.log : () => {},
        reconnectDelay: 5000,
        heartbeatIncoming: 4000,
        heartbeatOutgoing: 4000,
      });

      this.client.onConnect = (frame) => {
        console.log('WebSocket connected:', frame);
        this.isConnected = true;
        this.reconnectAttempts = 0;
        this.onConnect();
      };

      this.client.onDisconnect = (frame) => {
        console.log('WebSocket disconnected:', frame);
        this.isConnected = false;
        this.onDisconnect();
      };

      this.client.onStompError = (frame) => {
        console.error('STOMP error:', frame);
        this.handleError(frame);
      };

      this.client.activate();
      
    } catch (error) {
      console.error('WebSocket connection failed:', error);
      this.scheduleReconnect();
    }
  }

  // Disconnect WebSocket
  disconnect() {
    if (this.client && this.isConnected) {
      this.client.deactivate();
      this.isConnected = false;
      this.subscriptions.clear();
      this.messageHandlers.clear();
    }
  }

  // Subscribe to Chat Messages
  subscribeToChatMessages(chatId, callback) {
    if (!this.isConnected) {
      console.warn('WebSocket not connected');
      return null;
    }

    const destination = `/topic/chat/${chatId}`;
    const subscription = this.client.subscribe(destination, (message) => {
      try {
        const data = JSON.parse(message.body);
        callback(data);
      } catch (error) {
        console.error('Failed to parse message:', error);
      }
    });

    this.subscriptions.set(`chat-${chatId}`, subscription);
    return subscription;
  }

  // Subscribe to User Messages Queue
  subscribeToUserMessages(callback) {
    if (!this.isConnected) {
      console.warn('WebSocket not connected');
      return null;
    }

    const destination = '/user/queue/messages';
    const subscription = this.client.subscribe(destination, (message) => {
      try {
        const data = JSON.parse(message.body);
        callback(data);
      } catch (error) {
        console.error('Failed to parse user message:', error);
      }
    });

    this.subscriptions.set('user-messages', subscription);
    return subscription;
  }

  // Subscribe to User Status Updates
  subscribeToUserStatus(callback) {
    if (!this.isConnected) {
      console.warn('WebSocket not connected');
      return null;
    }

    const destination = '/topic/user-status';
    const subscription = this.client.subscribe(destination, (message) => {
      try {
        const data = JSON.parse(message.body);
        callback(data);
      } catch (error) {
        console.error('Failed to parse status update:', error);
      }
    });

    this.subscriptions.set('user-status', subscription);
    return subscription;
  }

  // Subscribe to Typing Indicators
  subscribeToTypingIndicators(chatId, callback) {
    if (!this.isConnected) {
      console.warn('WebSocket not connected');
      return null;
    }

    const destination = `/topic/chat/${chatId}/typing`;
    const subscription = this.client.subscribe(destination, (message) => {
      try {
        const data = JSON.parse(message.body);
        callback(data);
      } catch (error) {
        console.error('Failed to parse typing indicator:', error);
      }
    });

    this.subscriptions.set(`typing-${chatId}`, subscription);
    return subscription;
  }

  // Send Message via WebSocket
  sendMessage(chatId, content, replyToId = null) {
    if (!this.isConnected) {
      console.warn('WebSocket not connected, falling back to REST API');
      return false;
    }

    try {
      this.client.publish({
        destination: `/app/chat/${chatId}/send`,
        body: JSON.stringify({
          content,
          messageType: 'TEXT',
          replyToId,
        }),
      });
      return true;
    } catch (error) {
      console.error('Failed to send message via WebSocket:', error);
      return false;
    }
  }

  // Send Typing Indicator
  sendTypingIndicator(chatId, isTyping) {
    if (!this.isConnected) return false;

    try {
      this.client.publish({
        destination: `/app/chat/${chatId}/typing`,
        body: JSON.stringify({ isTyping }),
      });
      return true;
    } catch (error) {
      console.error('Failed to send typing indicator:', error);
      return false;
    }
  }

  // Mark Message as Read
  markMessageAsRead(messageId) {
    if (!this.isConnected) return false;

    try {
      this.client.publish({
        destination: `/app/message/${messageId}/read`,
        body: JSON.stringify({}),
      });
      return true;
    } catch (error) {
      console.error('Failed to mark message as read:', error);
      return false;
    }
  }

  // Send Heartbeat/Ping
  sendHeartbeat() {
    if (!this.isConnected) return false;

    try {
      this.client.publish({
        destination: '/app/ping',
        body: JSON.stringify({
          timestamp: Date.now(),
        }),
      });
      return true;
    } catch (error) {
      console.error('Failed to send heartbeat:', error);
      return false;
    }
  }

  // Unsubscribe from destination
  unsubscribe(subscriptionKey) {
    const subscription = this.subscriptions.get(subscriptionKey);
    if (subscription) {
      subscription.unsubscribe();
      this.subscriptions.delete(subscriptionKey);
    }
  }

  // Connection event handlers
  onConnect() {
    // Override in implementation
  }

  onDisconnect() {
    // Override in implementation
  }

  handleError(frame) {
    console.error('WebSocket STOMP error:', frame.headers['message']);
    this.scheduleReconnect();
  }

  // Reconnection logic
  scheduleReconnect() {
    if (this.reconnectAttempts < this.maxReconnectAttempts) {
      this.reconnectAttempts++;
      const delay = Math.pow(2, this.reconnectAttempts) * 1000; // Exponential backoff
      
      console.log(`Scheduling reconnect attempt ${this.reconnectAttempts} in ${delay}ms`);
      
      setTimeout(() => {
        if (!this.isConnected) {
          this.connect();
        }
      }, delay);
    } else {
      console.error('Max reconnection attempts reached');
    }
  }

  // Get connection status
  getConnectionStatus() {
    return {
      isConnected: this.isConnected,
      reconnectAttempts: this.reconnectAttempts,
    };
  }
}

export default new WebSocketService();
```

---

## 📁 File Upload Integration

### 1. File Upload Service

**services/fileUploadService.js**
```javascript
import apiClient from './apiClient';
import { Platform } from 'react-native';

class FileUploadService {
  
  // Upload Single File
  async uploadFile(file, onProgress = null) {
    try {
      const formData = new FormData();
      
      // Format file for React Native
      const fileObject = {
        uri: Platform.OS === 'ios' ? file.uri.replace('file://', '') : file.uri,
        type: file.type || 'application/octet-stream',
        name: file.fileName || file.name || `file_${Date.now()}`,
      };

      formData.append('file', fileObject);

      const response = await apiClient.post('/api/v2/upload/file', formData, {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
        onUploadProgress: (progressEvent) => {
          if (onProgress) {
            const percentCompleted = Math.round(
              (progressEvent.loaded * 100) / progressEvent.total
            );
            onProgress(percentCompleted);
          }
        },
      });

      return {
        success: true,
        data: response.data.data,
        message: response.data.message,
      };
    } catch (error) {
      return this.handleError(error);
    }
  }

  // Upload Multiple Files
  async uploadMultipleFiles(files, onProgress = null) {
    try {
      const formData = new FormData();
      
      files.forEach((file, index) => {
        const fileObject = {
          uri: Platform.OS === 'ios' ? file.uri.replace('file://', '') : file.uri,
          type: file.type || 'application/octet-stream',
          name: file.fileName || file.name || `file_${Date.now()}_${index}`,
        };
        
        formData.append('files', fileObject);
      });

      const response = await apiClient.post('/api/v2/upload/files', formData, {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
        onUploadProgress: (progressEvent) => {
          if (onProgress) {
            const percentCompleted = Math.round(
              (progressEvent.loaded * 100) / progressEvent.total
            );
            onProgress(percentCompleted);
          }
        },
      });

      return {
        success: true,
        data: response.data.data,
        message: response.data.message,
      };
    } catch (error) {
      return this.handleError(error);
    }
  }

  // Get File Info
  async getFileInfo(fileId) {
    try {
      const response = await apiClient.get(`/api/v2/upload/file/${fileId}/info`);
      
      return {
        success: true,
        data: response.data.data,
      };
    } catch (error) {
      return this.handleError(error);
    }
  }

  // Delete File
  async deleteFile(fileId) {
    try {
      const response = await apiClient.delete(`/api/v2/upload/file/${fileId}`);
      
      return {
        success: true,
        message: response.data.message,
      };
    } catch (error) {
      return this.handleError(error);
    }
  }

  // Validate File Before Upload
  validateFile(file, options = {}) {
    const {
      maxSize = 10 * 1024 * 1024, // 10MB default
      allowedTypes = ['image/jpeg', 'image/png', 'image/gif'],
    } = options;

    // Check file size
    if (file.fileSize && file.fileSize > maxSize) {
      return {
        valid: false,
        error: `File size exceeds maximum limit of ${Math.round(maxSize / (1024 * 1024))}MB`,
      };
    }

    // Check file type
    if (file.type && !allowedTypes.includes(file.type)) {
      return {
        valid: false,
        error: `File type ${file.type} is not allowed. Allowed types: ${allowedTypes.join(', ')}`,
      };
    }

    return { valid: true };
  }

  // Process Image for Upload (React Native specific)
  processImageFile(imagePickerResult) {
    if (imagePickerResult.assets && imagePickerResult.assets.length > 0) {
      return imagePickerResult.assets.map(asset => ({
        uri: asset.uri,
        type: asset.type,
        fileName: asset.fileName,
        fileSize: asset.fileSize,
        width: asset.width,
        height: asset.height,
      }));
    }
    
    return [];
  }

  handleError(error) {
    let message = 'File upload failed';
    
    if (error.response?.data?.message) {
      message = error.response.data.message;
    } else if (error.message) {
      message = error.message;
    }

    return {
      success: false,
      message,
      errors: error.response?.data?.fieldErrors,
    };
  }
}

export default new FileUploadService();
```

---

## ⚠️ Error Handling

### Global Error Handler

**utils/errorHandler.js**
```javascript
import { Alert } from 'react-native';

class ErrorHandler {
  
  // Handle API Errors
  static handleApiError(error, customMessage = null) {
    let title = 'Error';
    let message = customMessage || 'An unexpected error occurred. Please try again.';

    if (error.response) {
      // Server responded with error status
      const { status, data } = error.response;
      
      switch (status) {
        case 400:
          title = 'Invalid Request';
          message = data.message || 'The request contains invalid data.';
          break;
        case 401:
          title = 'Authentication Required';
          message = 'Please log in to continue.';
          // Handle token refresh or redirect to login
          break;
        case 403:
          title = 'Access Denied';
          message = 'You do not have permission to perform this action.';
          break;
        case 404:
          title = 'Not Found';
          message = data.message || 'The requested resource was not found.';
          break;
        case 409:
          title = 'Conflict';
          message = data.message || 'A conflict occurred while processing the request.';
          break;
        case 422:
          title = 'Validation Error';
          message = data.message || 'Please check your input and try again.';
          break;
        case 429:
          title = 'Too Many Requests';
          message = 'You are making requests too quickly. Please wait and try again.';
          break;
        case 500:
          title = 'Server Error';
          message = 'A server error occurred. Please try again later.';
          break;
        default:
          message = data.message || message;
      }
    } else if (error.request) {
      // Network error
      title = 'Network Error';
      message = 'Unable to connect to the server. Please check your internet connection.';
    }

    return { title, message };
  }

  // Show Error Alert
  static showErrorAlert(error, customMessage = null, onPress = null) {
    const { title, message } = this.handleApiError(error, customMessage);
    
    Alert.alert(
      title,
      message,
      [
        {
          text: 'OK',
          onPress: onPress || (() => {}),
        },
      ],
      { cancelable: false }
    );
  }

  // Handle Validation Errors
  static handleValidationErrors(errors) {
    if (!errors || typeof errors !== 'object') {
      return {};
    }

    const formattedErrors = {};
    Object.keys(errors).forEach(field => {
      formattedErrors[field] = Array.isArray(errors[field]) 
        ? errors[field][0] 
        : errors[field];
    });

    return formattedErrors;
  }

  // Network Status Handler
  static handleNetworkError(callback = null) {
    const title = 'No Internet Connection';
    const message = 'Please check your internet connection and try again.';
    
    Alert.alert(
      title,
      message,
      [
        {
          text: 'Retry',
          onPress: callback || (() => {}),
        },
        {
          text: 'Cancel',
          style: 'cancel',
        },
      ]
    );
  }
}

export default ErrorHandler;
```

---

## 🔄 State Management

### 1. Redux Store Setup

**store/index.js**
```javascript
import { configureStore } from '@reduxjs/toolkit';
import authSlice from './slices/authSlice';
import vehicleSlice from './slices/vehicleSlice';
import chatSlice from './slices/chatSlice';

export const store = configureStore({
  reducer: {
    auth: authSlice,
    vehicles: vehicleSlice,
    chat: chatSlice,
  },
  middleware: (getDefaultMiddleware) =>
    getDefaultMiddleware({
      serializableCheck: {
        ignoredActions: ['persist/PERSIST', 'persist/REHYDRATE'],
      },
    }),
});

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;
```

### 2. Auth Slice

**store/slices/authSlice.js**
```javascript
import { createSlice, createAsyncThunk } from '@reduxjs/toolkit';
import authService from '../../services/authService';

// Async Thunks
export const loginUser = createAsyncThunk(
  'auth/login',
  async (credentials, { rejectWithValue }) => {
    try {
      const result = await authService.login(credentials);
      if (result.success) {
        return result.data;
      } else {
        return rejectWithValue(result.message);
      }
    } catch (error) {
      return rejectWithValue(error.message);
    }
  }
);

export const registerUser = createAsyncThunk(
  'auth/register',
  async (userData, { rejectWithValue }) => {
    try {
      const result = await authService.register(userData);
      if (result.success) {
        return result.data;
      } else {
        return rejectWithValue(result.message);
      }
    } catch (error) {
      return rejectWithValue(error.message);
    }
  }
);

export const logoutUser = createAsyncThunk(
  'auth/logout',
  async (_, { rejectWithValue }) => {
    try {
      await authService.logout();
      return true;
    } catch (error) {
      return rejectWithValue(error.message);
    }
  }
);

const authSlice = createSlice({
  name: 'auth',
  initialState: {
    user: null,
    isAuthenticated: false,
    isLoading: false,
    error: null,
    tokens: {
      accessToken: null,
      refreshToken: null,
    },
  },
  reducers: {
    clearError: (state) => {
      state.error = null;
    },
    setUser: (state, action) => {
      state.user = action.payload;
      state.isAuthenticated = !!action.payload;
    },
    clearAuth: (state) => {
      state.user = null;
      state.isAuthenticated = false;
      state.tokens = { accessToken: null, refreshToken: null };
    },
  },
  extraReducers: (builder) => {
    builder
      // Login
      .addCase(loginUser.pending, (state) => {
        state.isLoading = true;
        state.error = null;
      })
      .addCase(loginUser.fulfilled, (state, action) => {
        state.isLoading = false;
        state.isAuthenticated = true;
        state.user = {
          userId: action.payload.userId,
          username: action.payload.username,
          email: action.payload.email,
          role: action.payload.role,
          location: action.payload.location,
        };
        state.tokens = {
          accessToken: action.payload.accessToken,
          refreshToken: action.payload.refreshToken,
        };
      })
      .addCase(loginUser.rejected, (state, action) => {
        state.isLoading = false;
        state.error = action.payload;
      })
      // Register
      .addCase(registerUser.pending, (state) => {
        state.isLoading = true;
        state.error = null;
      })
      .addCase(registerUser.fulfilled, (state, action) => {
        state.isLoading = false;
        // Note: Registration doesn't automatically log in
      })
      .addCase(registerUser.rejected, (state, action) => {
        state.isLoading = false;
        state.error = action.payload;
      })
      // Logout
      .addCase(logoutUser.pending, (state) => {
        state.isLoading = true;
      })
      .addCase(logoutUser.fulfilled, (state) => {
        state.isLoading = false;
        state.user = null;
        state.isAuthenticated = false;
        state.tokens = { accessToken: null, refreshToken: null };
      })
      .addCase(logoutUser.rejected, (state) => {
        state.isLoading = false;
        // Still clear auth even if logout API fails
        state.user = null;
        state.isAuthenticated = false;
        state.tokens = { accessToken: null, refreshToken: null };
      });
  },
});

export const { clearError, setUser, clearAuth } = authSlice.actions;
export default authSlice.reducer;
```

---

## 📱 React Native Specific Examples

### 1. Vehicle List Screen

**screens/VehicleListScreen.jsx**
```javascript
import React, { useState, useEffect, useCallback } from 'react';
import {
  View,
  FlatList,
  StyleSheet,
  RefreshControl,
  ActivityIndicator,
  Text,
} from 'react-native';
import { useDispatch, useSelector } from 'react-redux';
import { useFocusEffect } from '@react-navigation/native';

import VehicleCard from '../components/VehicleCard';
import SearchBar from '../components/SearchBar';
import FilterModal from '../components/FilterModal';
import vehicleService from '../services/vehicleService';
import ErrorHandler from '../utils/errorHandler';

const VehicleListScreen = ({ navigation }) => {
  const dispatch = useDispatch();
  const { user } = useSelector((state) => state.auth);
  
  const [vehicles, setVehicles] = useState([]);
  const [loading, setLoading] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState(null);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(true);
  const [filters, setFilters] = useState({});
  const [searchQuery, setSearchQuery] = useState('');

  useFocusEffect(
    useCallback(() => {
      loadVehicles(true);
    }, [filters, searchQuery])
  );

  const loadVehicles = async (reset = false) => {
    if (loading && !reset) return;

    try {
      setLoading(true);
      setError(null);

      const currentPage = reset ? 0 : page;
      const searchCriteria = {
        ...filters,
        ...(searchQuery && { query: searchQuery }),
      };

      const result = await vehicleService.searchVehicles(searchCriteria, {
        page: currentPage,
        size: 20,
        sort: 'createdAt,desc',
      });

      if (result.success) {
        const newVehicles = result.data.content;
        
        if (reset) {
          setVehicles(newVehicles);
          setPage(1);
        } else {
          setVehicles(prev => [...prev, ...newVehicles]);
          setPage(prev => prev + 1);
        }

        setHasMore(newVehicles.length === 20);
      } else {
        setError(result.message);
        ErrorHandler.showErrorAlert(new Error(result.message));
      }
    } catch (error) {
      setError(error.message);
      ErrorHandler.showErrorAlert(error);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  const onRefresh = () => {
    setRefreshing(true);
    loadVehicles(true);
  };

  const onLoadMore = () => {
    if (hasMore && !loading) {
      loadVehicles();
    }
  };

  const handleVehiclePress = (vehicle) => {
    // Track view
    vehicleService.trackVehicleView(vehicle.id);
    
    navigation.navigate('VehicleDetails', { 
      vehicleId: vehicle.id,
      vehicle,
    });
  };

  const handleShare = async (vehicle) => {
    try {
      // Track share
      await vehicleService.trackVehicleShare(vehicle.id, 'app');
      
      // Implement sharing logic
      // Share.share({
      //   message: `Check out this ${vehicle.year} ${vehicle.make} ${vehicle.model}`,
      //   url: `https://carworld.com/cars/${vehicle.id}`,
      // });
    } catch (error) {
      console.error('Share failed:', error);
    }
  };

  const handleFavorite = (vehicle) => {
    // Implement favorite logic
    console.log('Favorite:', vehicle.id);
  };

  const applyFilters = (newFilters) => {
    setFilters(newFilters);
    setPage(0);
  };

  const renderVehicleCard = ({ item }) => (
    <VehicleCard
      vehicle={item}
      onPress={handleVehiclePress}
      onShare={handleShare}
      onFavorite={handleFavorite}
    />
  );

  const renderFooter = () => {
    if (!loading) return null;
    
    return (
      <View style={styles.footer}>
        <ActivityIndicator size="small" color="#007AFF" />
      </View>
    );
  };

  const renderEmpty = () => {
    if (loading) return null;
    
    return (
      <View style={styles.empty}>
        <Text style={styles.emptyText}>
          {searchQuery || Object.keys(filters).length > 0
            ? 'No vehicles found matching your criteria'
            : 'No vehicles available'}
        </Text>
      </View>
    );
  };

  return (
    <View style={styles.container}>
      <SearchBar
        value={searchQuery}
        onChangeText={setSearchQuery}
        onFilterPress={() => navigation.navigate('FilterModal', { 
          currentFilters: filters,
          onApplyFilters: applyFilters,
        })}
        showFilter
      />

      <FlatList
        data={vehicles}
        renderItem={renderVehicleCard}
        keyExtractor={(item) => item.id.toString()}
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={onRefresh} />
        }
        onEndReached={onLoadMore}
        onEndReachedThreshold={0.1}
        ListFooterComponent={renderFooter}
        ListEmptyComponent={renderEmpty}
        showsVerticalScrollIndicator={false}
        contentContainerStyle={vehicles.length === 0 ? styles.emptyContainer : null}
      />
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
  },
  footer: {
    paddingVertical: 20,
    alignItems: 'center',
  },
  empty: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: 32,
  },
  emptyContainer: {
    flex: 1,
  },
  emptyText: {
    fontSize: 16,
    color: '#666',
    textAlign: 'center',
  },
});

export default VehicleListScreen;
```

---

## 🔧 Configuration Summary

### Environment Variables Required

Create a `.env` file in your React Native project:

```env
# API Configuration
API_BASE_URL_DEV=http://localhost:8080
API_BASE_URL_PROD=https://api.carworld.com
WS_URL_DEV=ws://localhost:8080/ws
WS_URL_PROD=wss://api.carworld.com/ws

# App Configuration
APP_NAME=Car Marketplace
APP_VERSION=1.0.0
```

### Required Dependencies

```json
{
  "dependencies": {
    "@react-native-async-storage/async-storage": "^1.19.0",
    "@reduxjs/toolkit": "^1.9.0",
    "@stomp/stompjs": "^7.0.0",
    "axios": "^1.4.0",
    "react-native-device-info": "^10.0.0",
    "react-native-image-picker": "^5.0.0",
    "react-native-vector-icons": "^10.0.0",
    "react-redux": "^8.1.0",
    "sockjs-client": "^1.6.0"
  }
}
```

---

This comprehensive integration guide provides all the necessary code and examples to integrate your React Native frontend with the Car Marketplace backend APIs. The examples include proper error handling, state management, WebSocket integration, and React Native-specific implementations.

For additional features or specific customizations, refer to the individual API endpoints documented in the main API requirements file.
