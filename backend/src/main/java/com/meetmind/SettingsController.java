package com.meetmind;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settings")
public class SettingsController {
    private final SettingsRepository settings;
    public SettingsController(SettingsRepository settings) { this.settings = settings; }
    @GetMapping public Map<String, String> get() { return settings.all(); }
    @PutMapping public ResponseEntity<Map<String, String>> update(@RequestBody Map<String, String> updates) { settings.save(updates); return ResponseEntity.ok(settings.all()); }
}
