package com.impulsecontrol.lend.auth;

import com.google.common.base.Optional;
import com.impulsecontrol.lend.model.User;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.yammer.dropwizard.auth.AuthenticationException;
import com.yammer.dropwizard.auth.Authenticator;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.HttpClients;
import org.json.JSONObject;
import org.mongojack.JacksonDBCollection;
import org.mongojack.WriteResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;

/**
 * Created by kerrk on 7/8/16.
 */
public class LendAuthenticator implements Authenticator<Credentials, User> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LendAuthenticator.class);

    private JacksonDBCollection<User, String> userCollection;

    private HttpClient client = HttpClients.createDefault();


    public LendAuthenticator(JacksonDBCollection<User, String> userCollection) {
        this.userCollection = userCollection;
    }

    public Optional<User> authenticate(Credentials credentials) throws AuthenticationException {
        if (credentials.getToken().isEmpty()) {
            throw new AuthenticationException("Invalid credentials");

        } else {
            try {
                URIBuilder builder = new URIBuilder("https://graph.facebook.com/debug_token")
                        .addParameter("input_token", credentials.getToken())
                        .addParameter("access_token", "{figure out how to store this}");

                HttpGet httpGet = new HttpGet(builder.toString());
                HttpResponse httpResp = client.execute(httpGet);

                String userId = extractUserId(httpResp);
                DBObject searchById = new BasicDBObject("userId", userId);
                User user = userCollection.findOne(searchById);
                if (user == null) {
                    User newUser = createNewUser(userId);
                    return Optional.fromNullable(newUser);
                }
                return Optional.fromNullable(user);
            } catch (IOException e) {
                throw new AuthenticationException("could not authenticate: " + e.getMessage());
            } catch (URISyntaxException e) {
                throw new AuthenticationException("could not authenticate: " + e.getMessage());
            }
        }
    }

    private String extractUserId(HttpResponse httpResp) throws AuthenticationException, IOException {
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
        return (String) userObj.get("user_id");
    }

    private User createNewUser(String userId) throws IOException, URISyntaxException {
        URIBuilder builder = new URIBuilder("https://graph.facebook.com/" + userId)
                .addParameter("access_token", "{figure out how to store this}");
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
        newUser.setFirstName(names[0]);

        newUser.setLastName(names[names.length - 1]);
        newUser.setUserId(userId);
        //TODO: check for error
        WriteResult<User, String> insertedUser = userCollection.insert(newUser);
        newUser = insertedUser.getSavedObject();
        return newUser;
    }
}
