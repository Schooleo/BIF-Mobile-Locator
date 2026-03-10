package com.bif.app.core.utils;

import android.net.Uri;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(AndroidJUnit4.class)
public class UriUtilsTest {

    @Test
    public void buildUri_withSchemeAuthorityPath_buildsCorrectly() {
        Uri uri = UriUtils.buildUri("https", "example.com", "/test");
        assertEquals("https", uri.getScheme());
        assertEquals("example.com", uri.getAuthority());
        assertEquals("/test", uri.getPath());
    }

    @Test
    public void buildUri_withPath_buildsWithDefaults() {
        Uri uri = UriUtils.buildUri("test");
        assertEquals("app", uri.getScheme());
        assertEquals("bif.app", uri.getAuthority());
        assertEquals("/test", uri.getPath());
    }

    @Test
    public void buildUri_withEmptyPath_buildsWithDefaultPath() {
        Uri uri = UriUtils.buildUri("");
        assertEquals("app", uri.getScheme());
        assertEquals("bif.app", uri.getAuthority());
        assertEquals("/map", uri.getPath());
    }

    @Test
    public void buildUri_withPathToEnum_buildsCorrectPath() {
        assertEquals("/map", UriUtils.buildUri(UriUtils.PathTo.MAP).getPath());
        assertEquals("/favorites", UriUtils.buildUri(UriUtils.PathTo.FAVORITES).getPath());
        assertEquals("/favorites/detail", UriUtils.buildUri(UriUtils.PathTo.FAVORITES_DETAIL).getPath());
        assertEquals("/social", UriUtils.buildUri(UriUtils.PathTo.SOCIAL).getPath());
        assertEquals("/profile", UriUtils.buildUri(UriUtils.PathTo.PROFILE).getPath());
        assertEquals("/login", UriUtils.buildUri(UriUtils.PathTo.LOGIN).getPath());
        assertEquals("/register", UriUtils.buildUri(UriUtils.PathTo.REGISTER).getPath());
    }

    @Test
    public void buildUri_noArgs_buildsDefault() {
        Uri uri = UriUtils.buildUri();
        assertEquals("app", uri.getScheme());
        assertEquals("bif.app", uri.getAuthority());
        assertEquals("/map", uri.getPath());
    }
}
