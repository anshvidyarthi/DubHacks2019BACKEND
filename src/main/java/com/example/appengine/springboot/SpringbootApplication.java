/*
 * Copyright 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.appengine.springboot;

// [START gae_java11_helloworld]
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.*;


import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@SpringBootApplication
@RestController
public class SpringbootApplication {

  private static final String APPLICATION_NAME = "Google Calendar API Java Quickstart";
  private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
  private static final String TOKENS_DIRECTORY_PATH = "tokens";
  private static final List<String> SCOPES = Collections.singletonList(CalendarScopes.CALENDAR);
  private static final String CREDENTIALS_FILE_PATH = "/credentials.json";


  public static void main(String[] args) {
    SpringApplication.run(SpringbootApplication.class, args);
  }

  @GetMapping("/")
  @RequestMapping(method = RequestMethod.GET, value = "/{name}/{duration}")
  public String hello(@PathVariable String name, @PathVariable long duration) throws IOException, GeneralSecurityException {
    // Build a new authorized API client service.
    final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
    Calendar service = new Calendar.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
            .setApplicationName(APPLICATION_NAME)
            .build();

    // Get list of times when we are busy
    DateTime now = new DateTime(System.currentTimeMillis() + 3600000);
    DateTime weekFromNow = new DateTime(System.currentTimeMillis() + 3600000 + 604800000);
    FreeBusyRequestItem item = new FreeBusyRequestItem().setId(service.calendars().get("primary").getCalendarId());
    ArrayList<FreeBusyRequestItem> bullshitList = new ArrayList<>();
    bullshitList.add(item);
    FreeBusyRequest request = new FreeBusyRequest();
    request.setTimeMin(now);
    request.setTimeMax(weekFromNow);
    request.setItems(bullshitList);
    FreeBusyCalendar freeBusyCalendar = service.freebusy().query(request).execute().getCalendars().get("primary");

    // insert an event
    List<TimePeriod> freeTimes = getFreeTimes(freeBusyCalendar.getBusy());

    boolean found = false;
    for (int i = 0; i < freeTimes.size() && !found; i++) {
      long freeTimeDuration = freeTimes.get(i).getEnd().getValue() - freeTimes.get(i).getStart().getValue();
      if (freeTimeDuration >= duration) {
        Event event = new Event().setSummary(name);
        DateTime eventStart = freeTimes.get(i).getStart();
        DateTime eventEnd = new DateTime(eventStart.getValue() + duration);
        EventDateTime eventDateTimeStart = new EventDateTime()
                .setDateTime(eventStart)
                .setTimeZone("America/Los_Angeles");
        EventDateTime eventDateTimeEnd = new EventDateTime()
                .setDateTime(eventEnd)
                .setTimeZone("America/Los_Angeles");

        event.setStart(eventDateTimeStart);
        event.setEnd(eventDateTimeEnd);
        service.events().insert("primary", event).execute();
        found = true;
      }
    }

    return Arrays.toString(getFreeTimes(freeBusyCalendar.getBusy()).toArray());
//    Events events = service.events().list("primary")
//            .setMaxResults(10)
//            .setTimeMin(now)
//            .setOrderBy("startTime")
//            .setSingleEvents(true)
//            .execute();
//    List<Event> items = events.getItems();
//    if (items.isEmpty()) {
//      return ("No upcoming events found.");
//    } else {
//      return "Found events";
//      System.out.println("Upcoming events");
//      for (Event event : items) {
//        DateTime start = event.getStart().getDateTime();
//        if (start == null) {
//          start = event.getStart().getDate();
//        }
//        System.out.printf("%s (%s)\n", event.getSummary(), start);
//      }
  }

  private List<TimePeriod> getFreeTimes(List<TimePeriod> busyTimes) {
    List<TimePeriod> freeTimes = new ArrayList<>();
    TimePeriod start = new TimePeriod().setStart(new DateTime(System.currentTimeMillis() + 3600000));
    if (busyTimes.isEmpty()) {
      start.setEnd(new DateTime(System.currentTimeMillis() + 604800000 + 3600000));
      freeTimes.add(start);
    } else {
      start.setEnd(busyTimes.get(0).getStart());
      freeTimes.add(start);

      if (busyTimes.size() > 1) {
        for (int i = 1; i < busyTimes.size(); i++) {
          TimePeriod freeTime = new TimePeriod().setStart(busyTimes.get(i - 1).getEnd())
                  .setEnd(busyTimes.get(i).getStart());
          freeTimes.add(freeTime);
        }
      }

      TimePeriod last = new TimePeriod().setStart(busyTimes.get(busyTimes.size() - 1).getEnd())
              .setEnd(new DateTime(System.currentTimeMillis() + 604800000 + 3600000));

      freeTimes.add(last);
    }

    return freeTimes;
  }

  /**
   * Creates an authorized Credential object.
   * @param HTTP_TRANSPORT The network HTTP Transport.
   * @return An authorized Credential object.
   * @throws IOException If the credentials.json file cannot be found.
   */
  private static Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT) throws IOException {
    // Load client secrets.
    InputStream in = SpringbootApplication.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
    if (in == null) {
      throw new FileNotFoundException("Resource not found: " + CREDENTIALS_FILE_PATH);
    }
    GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

    // Build flow and trigger user authorization request.
    GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
            HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
            .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
            .setAccessType("offline")
            .build();
    LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();

    return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
  }
}
// [END gae_java11_helloworld]
