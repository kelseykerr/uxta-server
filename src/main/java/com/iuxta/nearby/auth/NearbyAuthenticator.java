package com.iuxta.nearby.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.iuxta.nearby.NearbyUtils;
import com.iuxta.nearby.exception.InternalServerException;
import com.iuxta.nearby.firebase.CcsServer;
import com.iuxta.nearby.firebase.FirebaseUtils;
import com.iuxta.nearby.model.User;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import io.dropwizard.auth.AuthenticationException;
import io.dropwizard.auth.Authenticator;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.json.JSONException;
import org.json.JSONObject;
import org.mongojack.DBCursor;
import org.mongojack.JacksonDBCollection;
import org.mongojack.WriteResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Created by kerrk on 7/8/16.
 */
public class NearbyAuthenticator implements Authenticator<Credentials, User> {

    private static final Logger LOGGER = LoggerFactory.getLogger(NearbyAuthenticator.class);

    private JacksonDBCollection<User, String> userCollection;

    private CloseableHttpClient client = HttpClients.createDefault();

    private String fbAuthToken;

    private static HttpTransport httpTransport;

    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    private CcsServer ccsServer;


    GoogleIdTokenVerifier verifier;


    public NearbyAuthenticator(JacksonDBCollection<User, String> userCollection, String fbAuthToken, List<String> googleClientIds, CcsServer ccsServer) {
        this.userCollection = userCollection;
        this.fbAuthToken = fbAuthToken;
        this.ccsServer = ccsServer;
        try {
            this.httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            verifier = new GoogleIdTokenVerifier.Builder(httpTransport, JSON_FACTORY)
                    .setAudience(googleClientIds)
                    .build();
            LOGGER.info("Successfully set up google http transport for authentication!");
        } catch (Exception e) {
            String err = "could not set up http transport for google auth: " + e.getMessage();
            LOGGER.error(err);
            throw new InternalServerException(err);
        }
    }

    public NearbyAuthenticator() {

    }

    public Optional<User> authenticate(Credentials credentials) throws AuthenticationException {
        if (credentials.getToken().isEmpty()) {
            throw new AuthenticationException("Invalid credentials - token was not present");
        } else {
            if (credentials.getMethod().equals(NearbyUtils.GOOGLE_AUTH_METHOD)) {
                User user = doGoogleAuth(credentials);
                return Optional.ofNullable(user);
            } else {
                User user = doFacebookAuth(credentials);
                return Optional.ofNullable(user);
            }

        }
    }

    private User doGoogleAuth(Credentials credentials) throws AuthenticationException {
        LOGGER.info("attempting to authenticate with google");
        LOGGER.info("Google Auth token [" + credentials.getToken() + "]");
        GoogleIdToken idToken;
        try {
            idToken = verifier.verify(credentials.getToken());
        } catch (Exception e) {
            String error = "Unable to verify token [" + credentials.getToken() + "] with google, got error: " + e.getMessage();
            LOGGER.error(error);
            throw new AuthenticationException(error);
        }
        if (idToken != null) {
            GoogleIdToken.Payload payload = idToken.getPayload();

            // Print user identifier
            String userId = payload.getSubject();
            LOGGER.info("Google User ID: " + userId);
            User user = searchForExistingUser(userId);
            if (user == null) {
                user = createNewGoogleUser(payload, credentials.getIp());
                sendAdminsNotificationOfNewUser(user.getName());
                return user;
            }
            if (StringUtils.isNotBlank(credentials.getIp()) && (user.getTosAccepted() == null || !user.getTosAccepted())) {
                user.setTosAccepted(true);
                Date date = new Date();
                user.setTimeTosAccepted(date);
                user.setTosAcceptIp(credentials.getIp());
            } else if (user.getTosAccepted() == null) {
                user.setTosAccepted(false);
            }
            LOGGER.info("finished updating google user [" + user.getEmail() + "]");
            user.setAuthMethod(NearbyUtils.GOOGLE_AUTH_METHOD);
            userCollection.save(user);
            return user;

        } else {
            throw new AuthenticationException("could not authenticate: " + "invalid google auth token");
        }
    }

    private User doFacebookAuth(Credentials credentials) throws AuthenticationException {
        LOGGER.info("attempting to authenticate with facebook");
        try {
            URIBuilder builder = new URIBuilder("https://graph.facebook.com/debug_token")
                    .addParameter("input_token", credentials.getToken())
                    .addParameter("access_token", fbAuthToken);

            HttpGet httpGet = new HttpGet(builder.toString());
            CloseableHttpResponse httpResp = client.execute(httpGet);

            String userId = extractUserId(httpResp);
            User user = searchForExistingUser(userId);
            if (user == null) {
                User newUser = createNewFacebookUser(userId, credentials.getIp());
                sendAdminsNotificationOfNewUser(newUser.getName());
                return newUser;
            }
            if (StringUtils.isNotBlank(credentials.getIp()) && (user.getTosAccepted() == null || !user.getTosAccepted())) {
                user.setTosAccepted(true);
                Date date = new Date();
                user.setTimeTosAccepted(date);
                user.setTosAcceptIp(credentials.getIp());
            } else if (user.getTosAccepted() == null) {
                user.setTosAccepted(false);
            }
            user.setAuthMethod(NearbyUtils.FB_AUTH_METHOD);
            userCollection.save(user);
            LOGGER.info("authenticated user [" + (user.getEmail() != null ? user.getEmail() : user.getId()) + "]");
            return user;
        } catch (URISyntaxException e) {
            String message = "Could not construct uri, got error: " + e.getMessage();
            LOGGER.error(message);
            throw new AuthenticationException(message);
        } catch (IOException e) {
            String message = "Received an error authenticating with facebook: " + e.getMessage();
            LOGGER.error(message);
            throw new AuthenticationException(message);
        }
    }

    private User searchForExistingUser(String userId) {
        DBObject searchById = new BasicDBObject("userId", userId);
        User user = userCollection.findOne(searchById);
        return user;
    }

    private void sendAdminsNotificationOfNewUser(String username) {
        DBObject findAdmins = new BasicDBObject("admin", true);
        DBCursor cursor = userCollection.find(findAdmins);
        List<User> admins = cursor.toArray();
        if (admins != null && admins.size() > 0) {
            JSONObject notification = new JSONObject();
            notification.put("title", "New User Signed Up!");
            String body = "User [" + username + "] signed up!";
            notification.put("message", body);
            notification.put("type", FirebaseUtils.NotificationTypes.new_user_notification.name());
            for (User admin:admins) {
                FirebaseUtils.sendFcmMessage(admin, null, notification, ccsServer);
            }        }
    }

    private String extractUserId(CloseableHttpResponse httpResp) throws AuthenticationException, IOException {
        int code = httpResp.getStatusLine().getStatusCode();
        if (code != HttpStatus.SC_OK) {
            throw new AuthenticationException("Invalid credentials: " + httpResp.getStatusLine());
        }
        BufferedReader rd = new BufferedReader(new InputStreamReader(httpResp.getEntity().getContent()));
        StringBuilder result = new StringBuilder();
        String line;
        while ((line = rd.readLine()) != null) {
            result.append(line);
        }
        JSONObject dataObject = new JSONObject(result.toString());
        JSONObject userObj = (JSONObject) dataObject.get("data");
        httpResp.close();
        return (String) userObj.get("user_id");
    }

    /*private User updateGoogleUser(User user, GoogleIdToken.Payload payload) {
        LOGGER.info("updating google user [" + user.getEmail() + "]");
        String name = (String) payload.get("name");
        LOGGER.info("fetched google user [" + user.getEmail() + "]'s name: " + name);
        String pictureUrl = (String) payload.get("picture");
        LOGGER.info("fetched google user [" + user.getEmail() + "]'s picture: " + pictureUrl);
        String familyName = (String) payload.get("family_name");
        LOGGER.info("fetched google user [" + user.getEmail() + "]'s family name: " + familyName);
        String givenName = (String) payload.get("given_name");
        LOGGER.info("fetched google user [" + user.getEmail() + "]'s given name: " + givenName);
        boolean updatedName = name != null && !user.getName().equals(name);
        if (updatedName) {
            user.setName(name);
        }
        boolean updatedPicture = user.getPictureUrl() == null || (pictureUrl != null && !user.getPictureUrl().equals(pictureUrl));
        if (updatedPicture) {
            user.setPictureUrl(pictureUrl);
        }
        boolean updatedLastname = familyName != null && !user.getLastName().equals(familyName);
        if (updatedLastname) {
            user.setLastName(familyName);
        }
        boolean updatedFirstname = givenName != null && !user.getFirstName().equals(givenName);
        if (updatedFirstname) {
            user.setFirstName(givenName);
        }
        if (user.getTosAccepted() == null) {
            user.setTosAccepted(false);
        }
        userCollection.save(user);
        LOGGER.info("successfully wrote google user [" + user.getEmail() + "]'s info to the database");
        return user;
    }*/

    private User createNewGoogleUser(GoogleIdToken.Payload payload, String ip) {
        // Get profile information from payload
        String email = payload.getEmail();
        String name = (String) payload.get("name");
        String pictureUrl = (String) payload.get("picture");
        String familyName = (String) payload.get("family_name");
        String givenName = (String) payload.get("given_name");

        User newUser = new User();
        newUser.setCreatedDate(new Date());
        newUser.setName(name);
        newUser.setFirstName(givenName);
        newUser.setLastName(familyName);
        newUser.setUserId(payload.getSubject());
        newUser.setEmail(email);
        newUser.setPictureUrl(pictureUrl);
        if (StringUtils.isNotBlank(ip)) {
            newUser.setTosAccepted(true);
            Date date = new Date();
            newUser.setTimeTosAccepted(date);
            newUser.setTosAcceptIp(ip);
        } else {
            newUser.setTosAccepted(false);
        }
        newUser.setAuthMethod(NearbyUtils.GOOGLE_AUTH_METHOD);
        //TODO: check for error
        WriteResult<User, String> insertedUser = userCollection.insert(newUser);
        newUser = insertedUser.getSavedObject();
        return newUser;
    }

    private JSONObject getFbProfile(String userId) throws IOException, URISyntaxException {
        URIBuilder builder = new URIBuilder("https://graph.facebook.com/" + userId)
                .addParameter("access_token", fbAuthToken);
        HttpGet httpGet = new HttpGet(builder.toString());
        HttpResponse httpResp = client.execute(httpGet);
        BufferedReader rd = new BufferedReader(new InputStreamReader(httpResp.getEntity().getContent()));
        StringBuilder result = new StringBuilder();
        String line;
        while ((line = rd.readLine()) != null) {
            result.append(line);
        }
        JSONObject userInfo = new JSONObject(result.toString());
        return userInfo;
    }

    private User createNewFacebookUser(String userId, String ip) throws IOException, URISyntaxException {
        URIBuilder builder = new URIBuilder("https://graph.facebook.com/" + userId)
                .addParameter("access_token", fbAuthToken);
        HttpGet httpGet = new HttpGet(builder.toString());
        HttpResponse httpResp = client.execute(httpGet);
        BufferedReader rd = new BufferedReader(new InputStreamReader(httpResp.getEntity().getContent()));
        StringBuilder result = new StringBuilder();
        String line;
        while ((line = rd.readLine()) != null) {
            result.append(line);
        }
        JSONObject userInfo = new JSONObject(result.toString());
        String userName = (String) userInfo.get("name");
        String[] names = userName.split(" ");
        User newUser = new User();
        newUser.setCreatedDate(new Date());
        newUser.setName(userName);
        newUser.setFirstName(names[0]);
        newUser.setLastName(names[names.length - 1]);
        newUser.setUserId(userId);
        newUser.setAuthMethod(NearbyUtils.FB_AUTH_METHOD);
        try {
            String email = (String) userInfo.get("email");
            newUser.setEmail(email);
        } catch (JSONException e) {
            //do nothing
        }
        if (StringUtils.isNotBlank(ip)) {
            newUser.setTosAccepted(true);
            Date date = new Date();
            newUser.setTimeTosAccepted(date);
            newUser.setTosAcceptIp(ip);
        } else {
            newUser.setTosAccepted(false);
        }
        WriteResult<User, String> insertedUser = userCollection.insert(newUser);
        newUser = insertedUser.getSavedObject();
        return newUser;
    }

}
