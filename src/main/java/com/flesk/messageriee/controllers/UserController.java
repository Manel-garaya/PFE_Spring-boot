package com.flesk.messageriee.controllers;
import com.flesk.messageriee.Security.JwtTokenService;
import com.flesk.messageriee.Security.PasswordEncoderService;
import com.flesk.messageriee.Security.UserDetailsImpl;
import com.flesk.messageriee.models.Message;
import com.flesk.messageriee.services.UserService;
import com.flesk.messageriee.models.User;
import com.flesk.messageriee.models.ForgotPasswordRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.*;


@RestController
@RequestMapping("/api/users")
public class UserController {

    private String jwt;
    private UserDetailsImpl userDetails;
    private final UserService userService;
    private final PasswordEncoderService passwordEncoderService;
    private final JwtTokenService jwtTokenService;
    private AuthenticationManager authenticationManager;

    @Autowired
    public UserController(UserService userService, PasswordEncoderService passwordEncoderService, JwtTokenService jwtTokenService) {
        this.userService = userService;
        this.passwordEncoderService = passwordEncoderService;
        this.jwtTokenService = jwtTokenService;
    }


    @Autowired
    public void setAuthenticationManager(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    @GetMapping
    public Map<String, List<User>> getAllUsers() {
        List<User> users = userService.getAllUsers();
        Map<String, List<User>> response = new HashMap<>();
        response.put("data", users);
        return response;
    }

    @GetMapping("/{id}")
    public ResponseEntity<User> getUserById(@PathVariable String id) {
        Optional<User> user = userService.getUserById(id);
        return user.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/email")
    public ResponseEntity<User> getUserByEmail(@RequestParam("email") String email) {
        User user = userService.findByEmail(email);
        if (user != null) {
            return ResponseEntity.ok(user);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/saveuser")
    public ResponseEntity<User> saveUser(@RequestBody User user) {
        User savedUser = userService.saveUser(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedUser);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable String id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/register")
    public ResponseEntity<User> registerUser(@RequestBody User user) {
        if (userService.existsByUsername(user.getUsername())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(null);
        }

        if (userService.existsByEmail(user.getEmail())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(null);
        }

        String hashedPassword = passwordEncoderService.encodePassword(user.getPassword());
        user.setPassword(hashedPassword);

        User savedUser = userService.saveUser(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedUser);
    }


    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> loginUser(@RequestBody User user) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        user.getUsername(),
                        user.getPassword()

                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        String jwt = jwtTokenService.generateToken(user.getUsername());

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();

        // Créer un objet Map pour contenir le jeton et les détails de l'utilisateur
        Map<String, Object> response = new HashMap<>();
        response.put("jwt", jwt);
        response.put("userDetails", userDetails);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        User user = userService.findByEmail(request.getEmail());
        if (user == null) {
            return ResponseEntity.badRequest().body("Email not found");
        }
        // Generate reset token and send reset email
        userService.processForgotPassword(user);
        return ResponseEntity.ok("Password reset instructions sent to your email.");
    }
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> resetPasswordRequest) {
        String resetCode = resetPasswordRequest.get("resetCode");
        // Vérifiez si le code de réinitialisation est valide
        User user = userService.findByResetCode(resetCode);
        if (user == null) {
            return ResponseEntity.badRequest().body("Code de réinitialisation invalide ou expiré.");
        }
        // Envoyer une réponse avec succès et renvoyer le code de réinitialisation
        return ResponseEntity.ok("Code de réinitialisation valide.");
    }


    @PostMapping("/save-new-password")
    public ResponseEntity<?> saveNewPassword(@RequestBody Map<String, String> newPasswordRequest) {
        String resetCode = newPasswordRequest.get("resetCode");
        String newPassword = newPasswordRequest.get("newPassword");
        String confirmNewPassword = newPasswordRequest.get("confirmNewPassword");

        // Assurez-vous que le nouveau mot de passe correspond au mot de passe de confirmation
        if (!newPassword.equals(confirmNewPassword)) {
            return ResponseEntity.badRequest().body("Le nouveau mot de passe ne correspond pas au mot de passe de confirmation");
        }

        // Vérifiez si le code de réinitialisation est valide
        User user = userService.findByResetCode(resetCode);
        if (user == null) {
            return ResponseEntity.badRequest().body("Code de réinitialisation invalide ou expiré.");
        }

        String hashedNewPassword = passwordEncoderService.encodePassword(newPassword);

        user.setPassword(hashedNewPassword);
        // Effacez le code de réinitialisation après l'avoir utilisé
        user.setResetCode(null);
        // Enregistrez l'utilisateur mis à jour dans la base de données
        userService.saveUser(user);

        return ResponseEntity.ok("Nouveau mot de passe enregistré avec succès");
    }


    @PutMapping("/{id}/edit-profile")
    public ResponseEntity<User> editUserProfile(@PathVariable String id, @RequestBody User updatedUser) {
        Optional<User> existingUserOptional = userService.getUserById(id);

        if (!existingUserOptional.isPresent()) {
            return ResponseEntity.notFound().build();
        }

        User existingUser = existingUserOptional.get();

        existingUser.setUsername(updatedUser.getUsername());
        existingUser.setEmail(updatedUser.getEmail());
        existingUser.setBirthday(updatedUser.getBirthday());

        User savedUser = userService.saveUser(existingUser);

        return ResponseEntity.ok(savedUser);
    }

    @PostMapping("/{id}/change-password")
    public ResponseEntity<?> changeUserPassword(@PathVariable String id, @RequestBody Map<String, String> passwordMap) {
        String oldPassword = passwordMap.get("oldPassword");
        String newPassword = passwordMap.get("newPassword");
        String confirmPassword = passwordMap.get("confirmPassword");

        if (!newPassword.equals(confirmPassword)) {
            return ResponseEntity.badRequest().body("Le nouveau mot de passe ne correspond pas au mot de passe de confirmation");
        }

        Optional<User> userOptional = userService.getUserById(id);

        if (!userOptional.isPresent()) {
            return ResponseEntity.notFound().build();
        }

        User user = userOptional.get();

        if (!passwordEncoderService.matches(oldPassword, user.getPassword())) {
            return ResponseEntity.badRequest().body("Ancien mot de passe incorrect");
        }

        String hashedNewPassword = passwordEncoderService.encodePassword(newPassword);
        user.setPassword(hashedNewPassword);

        userService.saveUser(user);

        return ResponseEntity.ok("Mot de passe changé avec succès");
    }



}