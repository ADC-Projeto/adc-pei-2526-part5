package pt.unl.fct.di.adc.firstwebapp.resources;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

import pt.unl.fct.di.adc.firstwebapp.util.JWTConfig;
import pt.unl.fct.di.adc.firstwebapp.authentication.JWTToken;
import pt.unl.fct.di.adc.firstwebapp.util.UserData;
import pt.unl.fct.di.adc.firstwebapp.util.LoginData;

import com.google.gson.Gson;
import com.auth0.jwt.interfaces.DecodedJWT;



@Path("/login")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
public class LoginResource {
	
	//Settings that must be in the database
	public static final String ADMIN = "Admin";
	public static final String BACKOFFICE = "Backoffice";
	public static final String REGULAR = "Regular";
		
	public static Map<String, UserData> users = new HashMap<String, UserData>();
	
	private static final Logger LOG = Logger.getLogger(LoginResource.class.getName());
	private final Gson g = new Gson();
	
	public LoginResource() {}
	
	@POST
	@Path("/")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response doLogin(LoginData data) {
		LOG.fine("Login attempt by user: " + data.username);
		
		if(!checkPassword(data)) {
			return Response.status(Status.FORBIDDEN).entity("Incorrect username or password.").build();
		}
		
		String role = REGULAR;
		if (data.username.equalsIgnoreCase("admin")) { // TODO: (for educational purposes) change it to have proper logic on your project
			role = ADMIN;
		}
		
        Map<String, Object> fields = new HashMap<>();
        // Substitute the following fields with the ones that could make sense for your project
        fields.put("role", role);
        fields.put("email", "user@example.com");
		
		String token = JWTToken.createJWT(data.username, fields);
        if (token == null) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Failed to create JWT.").build();
        }

        // Create and return a secure HTTP-only cookie with the JWT token
        NewCookie cookie = new NewCookie.Builder("session::adc")
                .value(token) // JWT token
                .path("/")
                .comment("JWT session token") 
                .maxAge((int) (JWTConfig.EXPIRATION_TIME / 1000))
                .secure(false) // (set to false if not using HTTPS, but **not recommended** for production)
                .httpOnly(true) 
                .build();

        return Response.ok().cookie(cookie).entity("{\"token\":\"" + token + "\"}").build();
    }

	
    public static boolean checkPermissions(Cookie cookie, String requiredRole) {
        if (cookie == null || cookie.getValue() == null) {
            return false;
        }

        DecodedJWT jwt = JWTToken.extractJWT(cookie.getValue());
        if (jwt == null || !JWTToken.validateJWT(cookie.getValue())) {
            return false;
        }

        String userRole = jwt.getClaim("role").asString();
        return convertRole(userRole) >= convertRole(requiredRole);
    }

    
    private static boolean checkPassword(LoginData data) {
        UserData user = users.get(data.username);
        return user != null && user.password.equals(data.password);
    }

    
    private static int convertRole(String role) {
        return switch (role) {
            case BACKOFFICE -> 1;
            case ADMIN -> 2;
            case REGULAR -> 0;
            default -> -1;
        };
    }
    

    @GET
    @Path("/{username}")
    public Response checkUsernameAvailable(@PathParam("username") String username) {
        boolean available = !users.containsKey(username);
        return Response.ok().entity(g.toJson(available)).build();
    }

    
    @POST
    @Path("/create")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createUser(UserData data) {
        LOG.fine("Attempting to create user with username: " + data.username);

        if (users.containsKey(data.username)) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity("User with username " + data.username + " already exists.").build();
        }

        users.put(data.username, data);
        return Response.ok().build();
    }
}
