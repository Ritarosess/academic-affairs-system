import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Generates the BCrypt hashes used by db/data.sql using Spring Security's own
 * encoder, so the stored hashes are guaranteed to verify at login time.
 */
public class HashGen {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);
        String raw = args.length > 0 ? args[0] : "123456";
        String hash = encoder.encode(raw);
        System.out.println("password = " + raw);
        System.out.println("hash     = " + hash);
        System.out.println("matches  = " + encoder.matches(raw, hash));
        System.out.println("prefix   = " + hash.substring(0, 7));
    }
}
