# Profile Image Integration Guide

This document explains how the frontend should work with profile images, which are stored as BLOBs in the database.

## Storage Model

- The `User` entity stores profile images as binary data:
  - `profileImage: byte[]` – raw image bytes (JPEG/PNG/WebP).
  - `profileImageContentType: String` – MIME type (e.g. `image/jpeg`).
- Images are **not** stored in an external file store; they live in the database as BLOB fields.

## REST Endpoints

### 1. Upload Profile Image

`POST /api/user/profile/image`

- Auth required: `USER`, `BUYER`, `DEALER`, or `ADMIN`.
- Request:
  - `multipart/form-data` with a single part named `file`.
  - Accepted content types: `image/jpeg`, `image/png`, `image/webp`.
  - Max size: 5 MB.
- Behavior:
  - Validates file type and size.
  - Stores `file.getBytes()` into `User.profileImage` and `file.getContentType()` into `profileImageContentType`.
  - Returns an `ApiResponse` with a payload like:

```json
{
  "success": true,
  "message": "Profile image uploaded successfully",
  "data": {
    "imageUrl": "/api/user/{userId}/image"
  }
}
```

Use `data.imageUrl` as the endpoint for fetching the binary image.

### 2. Get Current User Profile Image

`GET /api/user/profile/image`

- Auth required: `USER`, `BUYER`, `DEALER`, or `ADMIN`.
- Produces:
  - `image/jpeg`, `image/png`, or `image/webp` (depending on stored content type).
- Response body:
  - Raw `byte[]` of the image (BLOB) with correct `Content-Type` header.

Example usage in the browser:

```js
const img = document.getElementById('profile-img');
img.src = '/api/user/profile/image';
```

The browser will fetch the image as a binary response and render it directly.

### 3. Get Profile Image by User ID

`GET /api/user/{id}/image`

- Auth: public (or protected by your global auth rules), depending on how you expose user profiles.
- Produces the same image formats as `/profile/image`.
- Response: raw image bytes for the requested user.

This is the URL returned by `uploadProfileImage` in the `imageUrl` field.

### 4. Delete Current User Profile Image

`DELETE /api/user/profile/image`

- Auth required: `USER`, `BUYER`, `DEALER`, or `ADMIN`.
- Sets `profileImage` and `profileImageContentType` to `null` in the database.

## User Profile DTO

When fetching user profiles via:

- `GET /api/user/profile`
- `GET /api/user/{id}`

The backend returns a `UserResponse` DTO with the following relevant field:

- `profileImageUrl: String` – if a profile image exists, this is set to `/api/user/{id}/image`; otherwise it is `null`.

Example JSON fragment:

```json
{
  "id": 123,
  "username": "john.doe",
  "email": "john@example.com",
  "role": "USER",
  "profileImageUrl": "/api/user/123/image"
}
```

On the frontend, simply bind `profileImageUrl` to an `<img>` element if it is non-null.

```jsx
<img src={user.profileImageUrl || '/assets/default-avatar.png'} alt="Profile" />
```

## Working with BLOBs in Frontend Code

If you need more control (e.g. to show a loading skeleton or handle errors), you can fetch the image as a `Blob` and create an object URL:

```js
async function loadProfileImage() {
  const res = await fetch('/api/user/profile/image');
  if (!res.ok) {
    // handle missing image
    return;
  }
  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  document.getElementById('profile-img').src = url;
}
```

This is still using the BLOB data from the database; the only difference is that the browser wraps it as an object URL.

## Security & Performance Notes

- **Security**
  - Only the owner (and admins) can upload/delete their profile image.
  - Access control for `GET /api/user/{id}/image` can be tightened if you want profile images to be private.
- **Performance**
  - Images are small (<= 5 MB) and served directly from the backend as binary HTTP responses.
  - Consider adding caching headers (e.g. `ETag` or short-lived `Cache-Control`) if you want the browser/CDN to cache avatars.
