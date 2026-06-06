package com.main.json;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class JSONReader {
    public static JSONObject readJsonObjectFromUrl(String urlStr) throws Exception {
        try (InputStream is = new URL(urlStr).openStream();
             BufferedReader rd = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            int cp;
            while ((cp = rd.read()) != -1) sb.append((char) cp);
            return new JSONObject(sb.toString());
        }
    }

    public static JSONArray readJsonArrayFromUrl(String urlStr) throws Exception {
        try (InputStream is = new URL(urlStr).openStream();
             BufferedReader rd = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            int cp;
            while ((cp = rd.read()) != -1) sb.append((char) cp);
            return new JSONArray(sb.toString());
        }
    }
}
