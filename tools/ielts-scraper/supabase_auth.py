#!/usr/bin/env python3
"""Supabase sign-in for the loader, and a way to print an access token.

The backend accepts the access token of a signed-in Supabase user: a JWT that carries the user's
role in app_metadata.role. It is not the project's publishable (anon) key or service_role key.

The web app keeps that token in an httpOnly cookie and calls the backend from its server, so it
cannot be copied from the browser's Network tab. Signing in with email and password here gets one
directly, and lets the loader get a new one when it expires (after an hour by default):

    SUPABASE_URL=https://<project-ref>.supabase.co
    SUPABASE_PUBLISHABLE_KEY=<the value of NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY in apps/web/.env.local>
    ENGLOW_EMAIL=<an ADMIN or STAFF account>
    ENGLOW_PASSWORD=...

    python supabase_auth.py          # prints the access token and the role in it

ENGLOW_TOKEN, when set, is used as it is and nothing is signed in; it cannot be renewed.
"""

from __future__ import annotations

import base64
import json
import os
import sys
import urllib.error
import urllib.request


class AuthError(Exception):
    pass


class Auth:
    def __init__(self):
        self._token = os.environ.get("ENGLOW_TOKEN") or None
        self._url = (os.environ.get("SUPABASE_URL") or "").rstrip("/")
        self._key = os.environ.get("SUPABASE_PUBLISHABLE_KEY") or ""
        self._email = os.environ.get("ENGLOW_EMAIL") or ""
        self._password = os.environ.get("ENGLOW_PASSWORD") or ""
        self._refresh = None
        self.can_sign_in = bool(self._url and self._key and self._email and self._password)
        if not self._token and not self.can_sign_in:
            raise AuthError("Set ENGLOW_TOKEN, or SUPABASE_URL, SUPABASE_PUBLISHABLE_KEY, ENGLOW_EMAIL and "
                            "ENGLOW_PASSWORD so the scripts can sign in (see supabase_auth.py).")

    @property
    def token(self) -> str:
        if not self._token:
            self._sign_in()
        return self._token

    def renew(self) -> bool:
        """Get a fresh token after the backend refused the current one; False when there is no way to."""
        if not self.can_sign_in:
            return False
        if self._refresh:
            try:
                self._grant("refresh_token", {"refresh_token": self._refresh})
                return True
            except AuthError:
                pass  # a refresh token is single-use and can lapse; signing in again always works
        self._sign_in()
        return True

    def _sign_in(self) -> None:
        self._grant("password", {"email": self._email, "password": self._password})

    def _grant(self, kind: str, body: dict) -> None:
        request = urllib.request.Request(f"{self._url}/auth/v1/token?grant_type={kind}",
                                         data=json.dumps(body).encode(), method="POST")
        request.add_header("apikey", self._key)
        request.add_header("Content-Type", "application/json")
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                session = json.loads(response.read().decode())
        except urllib.error.HTTPError as error:
            detail = error.read().decode()[:300]
            raise AuthError(f"Supabase sign-in failed with {error.code}: {detail}") from error
        except urllib.error.URLError as error:
            raise AuthError(f"Supabase sign-in failed: {error.reason}") from error
        self._token, self._refresh = session["access_token"], session.get("refresh_token")


def role_in(token: str) -> str | None:
    """The app_metadata.role claim, read without checking the signature (the backend does that)."""
    try:
        payload = token.split(".")[1]
        claims = json.loads(base64.urlsafe_b64decode(payload + "=" * (-len(payload) % 4)))
        return (claims.get("app_metadata") or {}).get("role")
    except (IndexError, ValueError):
        return None


if __name__ == "__main__":
    try:
        token = Auth().token
    except AuthError as e:
        print(e, file=sys.stderr)
        sys.exit(2)
    print(token)
    print(f"role: {role_in(token) or 'none - the backend will refuse admin calls'}", file=sys.stderr)
