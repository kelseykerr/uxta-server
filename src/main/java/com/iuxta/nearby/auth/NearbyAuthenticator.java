package com.iuxta.nearby.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.iuxta.nearby.NearbyUtils;
import com.iuxta.nearby.exception.InternalServerException;
import com.iuxta.nearby.model.User;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import io.dropwizard.auth.AuthenticationException;
import io.dropwizard.auth.Authenticator;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.json.JSONException;
import org.json.JSONObject;
import org.mongojack.JacksonDBCollection;
import org.mongojack.WriteResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.util.Collections;
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

    private List<String> googleClientIds;

    private static HttpTransport httpTransport;

    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();


    GoogleIdTokenVerifier verifier;


    public NearbyAuthenticator(JacksonDBCollection<User, String> userCollection, String fbAuthToken, List<String> googleClientIds) {
        this.userCollection = userCollection;
        this.fbAuthToken = fbAuthToken;
        this.googleClientIds = googleClientIds;
        try {
            this.httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            verifier = new GoogleIdTokenVerifier.Builder(httpTransport, JSON_FACTORY)
                    //.setAudience(Collections.singletonList(this.googleClientId + ""))
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
            throw new AuthenticationException("Invalid credentials");

        } else {
            try {
                if (credentials.getMethod().equals(NearbyUtils.GOOGLE_AUTH_METHOD)) {
                    LOGGER.info("attempting to authenticate with google");
                    LOGGER.info("Google Auth token [" + credentials.getToken() + "]");
                    GoogleIdToken idToken = verifier.verify(credentials.getToken());
                    if (idToken != null) {
                        GoogleIdToken.Payload payload = idToken.getPayload();

                        // Print user identifier
                        String userId = payload.getSubject();
                        LOGGER.info("Google User ID: " + userId);

                        DBObject searchById = new BasicDBObject("userId", userId);
                        User user = userCollection.findOne(searchById);
                        if (user == null) {
                            String email = payload.getEmail();
                            boolean emailVerified = Boolean.valueOf(payload.getEmailVerified());
                            if (email != null && emailVerified) {
                                searchById = new BasicDBObject("email", email);
                                user = userCollection.findOne(searchById);
                            }
                            if (user == null) {
                                user = createNewGoogleUser(payload);
                                return Optional.of(user);
                            }
                        }
                        updateGoogleUser(user, payload);
                        LOGGER.info("finished updating google user [" + user.getEmail() + "]");
                        if (user.getTosAccepted() == null) {
                            user.setTosAccepted(false);
                        }
                        user.setAuthMethod(NearbyUtils.GOOGLE_AUTH_METHOD);
                        userCollection.save(user);
                        return Optional.of(user);

                    } else {
                        throw new AuthenticationException("could not authenticate: " + "invalid google auth token");
                    }
                } else {
                    LOGGER.info("attempting to authenticate with facebook");
                    URIBuilder builder = new URIBuilder("https://graph.facebook.com/debug_token")
                            .addParameter("input_token", credentials.getToken())
                            .addParameter("access_token", fbAuthToken);

                    HttpGet httpGet = new HttpGet(builder.toString());
                    CloseableHttpResponse httpResp = client.execute(httpGet);

                    String userId = extractUserId(httpResp);
                    DBObject searchById = new BasicDBObject("userId", userId);
                    User user = userCollection.findOne(searchById);
                    if (user == null) {
                        String email = getUserEmail(userId);
                        if (email != null) {
                            searchById = new BasicDBObject("email", email);
                            user = userCollection.findOne(searchById);
                        }
                        if (user == null) {
                            User newUser = createNewFacebookUser(userId);
                            return Optional.of(newUser);
                        }
                    }
                    if (user.getTosAccepted() == null) {
                        user.setTosAccepted(false);
                    }
                    user.setAuthMethod(NearbyUtils.FB_AUTH_METHOD);
                    userCollection.save(user);
                    return Optional.of(user);
                }
            } catch (Exception e) {
                throw new AuthenticationException("could not authenticate: " + e.getMessage());
            }
        }
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

    private String getUserEmail(String userId) throws IOException, URISyntaxException {
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
        try {
            String email = (String) userInfo.get("email");
            return email;
        } catch (JSONException e) {
            //do nothing
            return null;
        }
    }

    private User updateGoogleUser(User user, GoogleIdToken.Payload payload) {
        LOGGER.info("updating google user [" + user.getEmail() + "]");
        String name = (String) payload.get("name");
        String pictureUrl = (String) payload.get("picture");
        String familyName = (String) payload.get("family_name");
        String givenName = (String) payload.get("given_name");

        if (!user.getName().equals(name) || user.getPictureUrl() == null || !user.getPictureUrl().equals(pictureUrl)
                || !user.getLastName().equals(familyName) || !user.getLastName().equals(givenName)) {
            user.setName(name);
            user.setPictureUrl(pictureUrl);
            user.setLastName(familyName);
            user.setFirstName(givenName);
            userCollection.save(user);
            return user;
        } else {
            return user;
        }
    }

    private User createNewGoogleUser(GoogleIdToken.Payload payload) {
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
        newUser.setTosAccepted(false);
        newUser.setAuthMethod(NearbyUtils.GOOGLE_AUTH_METHOD);
        //TODO: check for error
        WriteResult<User, String> insertedUser = userCollection.insert(newUser);
        newUser = insertedUser.getSavedObject();
        return newUser;
    }

    private User createNewFacebookUser(String userId) throws IOException, URISyntaxException {
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
        newUser.setTosAccepted(false);
        //TODO: check for error
        WriteResult<User, String> insertedUser = userCollection.insert(newUser);
        newUser = insertedUser.getSavedObject();
        return newUser;
    }

    public JacksonDBCollection<User, String> getUserCollection() {
        return userCollection;
    }

    public void setUserCollection(JacksonDBCollection<User, String> userCollection) {
        this.userCollection = userCollection;
    }

    public String getFbAuthToken() {
        return fbAuthToken;
    }

    public void setFbAuthToken(String fbAuthToken) {
        this.fbAuthToken = fbAuthToken;
    }
}
