import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class formatter {

  //Constants to change artist and song amount
  private static final int NUMBER_OF_ARTIST = 5;
  private static final int NUMBER_OF_SONGS = 10;
  private static final int NUMBER_OF_STREAKS = 10;
  private static final int MIN_STREAK = 10;
  private static final int STREAK_TIME = 7200;
  private static final int SKIPPED_SECONDS = 10; //Set to 0 to turn off
  private static final boolean DUPLICATES_STORE = true; //Doesn't count them in statistics
  private static final boolean DUPLICATES_DISPLAY = true;

  public static void main(String[] args) {
    //Set up for finding the most listened to song
    Pair<String, Integer> currentSong = new Pair<>("", 0);
    List<Pair<String, Integer>> songStreaks = new ArrayList<>();
    int songStreak = 0;

    Pair<String, Integer> currentArtist = new Pair<>("", 0);
    List<Pair<String, Integer>> artistStreaks = new ArrayList<>();
    int artistStreak = 0;

    //Set up for finding the total listened to time
    AtomicInteger totalTime = new AtomicInteger();
    //Set up for finding the top <NUMBER_OF_ARTISTS> artists
    HashMap<String, Integer> artistMap  = new HashMap<>();

    //Set up for updating the time
    Integer oldTime;

    //Find the folder with the data in it
    File folder = new File(System.getProperty("user.dir") + File.separator + "data");
    JSONParser parser = new JSONParser();
    File[] files = folder.listFiles();

    if (files == null) {
      System.err.println("No files found in " + folder.getPath());
      return;
    }
    //To store all the songs
    HashMap<String, Integer> content = new HashMap<>();
    //Iterate over all the files
    for (File file : files) {
      try (FileReader reader = new FileReader(file)) {
        JSONArray array = (JSONArray) parser.parse(reader);
        for (Object o : array) {
          JSONObject object = (JSONObject) o;
          Object trackName = object.get("master_metadata_track_name"); //trackName
          Object artistName = object.get("master_metadata_album_artist_name"); //artistName
          Object timePlayed = object.get("ms_played");
          if (trackName == null || artistName == null || timePlayed == null) continue;

          //To make a unique name for the content list
          String name = trackName + " by " + artistName;
          //Getting the artist name
          String artist = artistName.toString();

          //Getting the time listened to (in milliseconds)
          int time = Integer.parseInt(timePlayed.toString()); //msPlayed

          //Don't count it if it was listened to for less than 10 seconds
          if (time / 1000 < SKIPPED_SECONDS) continue;

          //Highest song streak
          if (!name.equals(currentSong.getKey())) {  //If the names aren't the same
            if (songStreak >= MIN_STREAK) {
              if (DUPLICATES_STORE) songStreaks.add(new Pair<>(currentSong.getKey(), currentSong.getValue()));
              else {
                boolean flag = false;
                for (Pair<String, Integer> streak : songStreaks) {
                  String songName = streak.getKey();
                  if (songName.equals(name) && streak.getValue() < time) {
                    flag = true;
                    break;
                  }
                }
                if (!flag) songStreaks.add(new Pair<>(currentSong.getKey(), currentSong.getValue()));
              }
            }

            //Set the current song
            currentSong.setKey(name);
            currentSong.setValue(time);
            songStreak = 0;
          } else { //If the names are the same
            //Add to the current time
            currentSong.setValue(currentSong.getValue() + time);
            songStreak++;
          }

          //Highest artist streak
          if (!artist.equals(currentArtist.getKey())) {  //If the artists aren't the same

//            if (artistStreak >= MIN_STREAK) {
//              artistStreaks.add(new Pair<>(currentArtist.getKey(), currentArtist.getValue()));
//            }

            if (artistStreak >= MIN_STREAK) {
              if (DUPLICATES_STORE) artistStreaks.add(new Pair<>(currentArtist.getKey(), currentArtist.getValue()));
              else {
                boolean flag = false;
                for (Pair<String, Integer> streak : artistStreaks) {
                  String artistNameTemp = streak.getKey();
                  if (artistNameTemp.equals(artistName) && streak.getValue() < time) {
                    flag = true;
                    break;
                  }
                }
                if (!flag) artistStreaks.add(new Pair<>(currentArtist.getKey(), currentArtist.getValue()));
              }
            }

            //Set the current artist
            currentArtist.setKey(artist);
            currentArtist.setValue(time);
            artistStreak = 0;
          } else { //If the artists are the same
            //Add to the current time
            currentArtist.setValue(currentArtist.getValue() + time);
            artistStreak++;
          }

          //Add the song to the hashmap if it doesn't exist
          oldTime = content.putIfAbsent(name, time);
          if (oldTime != null) {
            //Add the time up if it does exist
            int contentTime = time + oldTime;
            content.replace(name, oldTime, contentTime);
          }

          //Add the artist to the hashmap if it doesn't exist
          oldTime = artistMap.putIfAbsent(artist, time);
          if (oldTime != null) {
            //Add the time  if it does exist
            int artistTime = time + oldTime;
            artistMap.replace(artist, oldTime, artistTime);
          }
        }
      }catch (Exception e) {
        System.err.println("Failed to read file " + file.getName());
      }
    }

    //Collect all the songs in the hashmap
    ArrayList<Pair<String, Integer>> contentList = new ArrayList<>();
    content.forEach((name, time) -> {
      contentList.add(new Pair<>(name, time / 1000));
      totalTime.set(totalTime.get() + (time / 1000));
    });

    //Sort the songs based on time
    contentList.sort(Comparator.comparing(Pair::getValue));

    //Get a list of the artist listened to
    List<Pair<String, Integer>> artistList = new ArrayList<>();
    artistMap.forEach((name, time) -> artistList.add(new Pair<>(name, time / 1000)));
    artistList.sort(Comparator.comparing(pair -> -pair.getValue()));

    //Sort the streaks lists
    songStreaks.sort(Comparator.comparing(pair -> -pair.getValue()));
    artistStreaks.sort(Comparator.comparing(pair -> -pair.getValue()));

    //Write the content to a file named "out.txt"
    File output = new File(System.getProperty("user.dir") + File.separator + "out.txt");
    try (PrintWriter writer = new PrintWriter(new FileWriter(output))) {
      //Output all the songs in ascending order
      contentList.forEach(pair -> writer.println("Name: " + pair.getKey() + "      Time: " + convertSecondsToTime(pair.getValue())));

      //Made a new section for the stats
      writer.println("--------------------------------Stats------------------------------------");
      writer.println("Total time: " + convertSecondsToTime(totalTime.get()));
      //Collecting unique artists and songs (< 30 seconds play time doesn't count as a listen)
      List<Pair<String, Integer>> uniqueSongs = contentList.stream().filter(pair -> pair.getValue() > 30).collect(
          Collectors.toList());
      List<Pair<String, Integer>> uniqueArtists = artistList.stream().filter(pair -> pair.getValue() > 30).collect(
          Collectors.toList());
      writer.println("Unique songs: " + uniqueSongs.size());
      writer.println("Unique artist: " + uniqueArtists.size());

      //Output top artist
      writer.println("Top " + NUMBER_OF_ARTIST + " artist:");
      for (int i = 0; i < NUMBER_OF_ARTIST; i++) {
        Pair<String, Integer> pair = artistList.get(i);
        writer.println("   -Name: " + pair.getKey() + "      Time: " + convertSecondsToTime(pair.getValue()));
      }

      //Output top songs
      writer.println("Top " + NUMBER_OF_SONGS + " songs:");
      contentList.sort(Comparator.comparing(pair -> -pair.getValue()));
      for (int i = 0; i < NUMBER_OF_SONGS; i++) {
        Pair<String, Integer> pair = contentList.get(i);
        writer.println("   -Name: " + pair.getKey() + "      Time: " + convertSecondsToTime(pair.getValue()));
      }

      // Output top streaks
      writer.println("Top " + NUMBER_OF_STREAKS + " song streaks:");
      for (int i = 0; i < NUMBER_OF_STREAKS; i++) {
        Pair<String, Integer> pair = songStreaks.get(i);
        writer.println(
            "   -Name: "
                + pair.getKey()
                + "      Time: "
                + convertSecondsToTime(pair.getValue() / 1000));
      }


      writer.println("Top " + NUMBER_OF_STREAKS + " artist streaks:");
      for (int i = 0; i < NUMBER_OF_STREAKS; i++) {
        Pair<String, Integer> pair = artistStreaks.get(i);
        writer.println(
            "   -Name: "
                + pair.getKey()
                + "      Time: "
                + convertSecondsToTime(pair.getValue() / 1000));
      }

      int streaks = (int) songStreaks.stream().filter(pair -> (pair.getValue() / 1000) >= STREAK_TIME)
          .count();
      writer.println("Streaks over " + STREAK_TIME / 60 + " minutes: " + streaks);

      int streakTime = 0;
      int counter = 0;
      for (Pair<String, Integer> streak : songStreaks) {
        int time = streak.getValue() / 1000;
        if (time < STREAK_TIME)
          continue;
        streakTime += time;
        counter++;
      }
      streakTime = streakTime / counter;
      writer.println("Average streak time (over " + STREAK_TIME / 60 + " minutes): " + convertSecondsToTime(streakTime));

      //TODO: Time/Date sorting/filtering
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

// This is for adding options in the future if I can be bothered
//  /**
//   * Function to output the statistics
//   * @param contentList the list of songs listened to
//   */
//  private static void output(ArrayList<Pair<String, Integer>> contentList){
//    output(contentList, 0);
//  }
//
//  /**
//   * Function to output the statistics
//   * @param contentList the list of songs listened to
//   * @param totalTime the total listen time in seconds
//   */
//  private static void output(ArrayList<Pair<String, Integer>> contentList, int totalTime) {
//
//  }

  /**
   * Convert the time in seconds to a readable format
   * @param totalSecs time in seconds
   * @return a string representing the time
   */
  private static String convertSecondsToTime(int totalSecs) {
    int hours = totalSecs / 3600;
    int minutes = (totalSecs % 3600) / 60;
    int seconds = totalSecs % 60;
    return (hours < 10 ? "0" : "") + hours + "h:" + (minutes < 10 ? "0" : "") + minutes + "m:"
        + (seconds < 10 ? "0" : "") + seconds + "s";
  }
}
