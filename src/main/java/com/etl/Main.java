package com.etl;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class Main {

    static class Movie {
        String tconst;
        String title;
        String year;
        String genre;
        String rating;
        String numVotes;
        List<String> topCast = new ArrayList<>();
        List<String> directors = new ArrayList<>();

        public Movie(String tconst, String title, String year, String genre) {
            this.tconst = tconst;
            this.title = title;
            this.year = year;
            this.genre = genre;
        }
    }

    public static void main(String[] args) throws IOException {
        // identifier, movie obj
        Map<String, Movie> movies = new HashMap<>();

        // read title.basic.tsv and adding int (title, year, genre) from titles.basics.tsv
        try (InputStream is = Main.class.getClassLoader().getResourceAsStream("data/title.basics.tsv");
             Reader in = new InputStreamReader(is, StandardCharsets.UTF_8)) {

            Iterable<CSVRecord> records = CSVFormat.TDF
                    .withFirstRecordAsHeader()
                    .withQuote(null)
                    .parse(in);

            for (var record : records) {
                String tconst = record.get("tconst");
                String titleType = record.get("titleType");
                String primaryTitle = record.get("primaryTitle");
                String startYear = record.get("startYear");
                String genres = record.get("genres");

                String isAdult = record.get("isAdult");
                if (!"movie".equalsIgnoreCase(titleType) || "\\N".equals(startYear) || "1".equals(isAdult)) {continue;}

                Movie movie = new Movie(tconst, primaryTitle, startYear, genres);
                // add to map
                movies.put(tconst, movie);
            }
        }
        // adding in (ratings and votes) from titles.ratings.csv
        try (InputStream is = Main.class.getClassLoader().getResourceAsStream("data/title.ratings.tsv");
             Reader in = new InputStreamReader(is, StandardCharsets.UTF_8)) {

            Iterable<CSVRecord> records = CSVFormat.TDF
                    .withFirstRecordAsHeader()
                    .withQuote(null)
                    .parse(in); // adding in the file

            for (var record : records) {
                String tconst = record.get("tconst");
                String rating = record.get("averageRating");
                String numVotes = record.get("numVotes");

                // check if movie is present map of movies to update the rest of info
                if (movies.containsKey(tconst)) {
                    Movie m = movies.get(tconst);
                    m.numVotes = numVotes;
                    m.rating = rating;
                }
            }
        }

        // link people to movies using title.principles.tsv
            //tconst - List<nconst>
        Map<String, List<String >> movieToDirectors = new HashMap<>();
        Map<String, List<String>> movieToTopCast = new HashMap<>();
        Set<String> allNconsts = new HashSet<>();

        try (InputStream is = Main.class.getClassLoader().getResourceAsStream("data/title.principals.tsv");
            Reader in = new InputStreamReader(is, StandardCharsets.UTF_8)) {

            Iterable<CSVRecord> records = CSVFormat.TDF
                    .withFirstRecordAsHeader()
                    .withQuote(null)
                    .parse(in);

            for (CSVRecord record : records) {
                String tconst = record.get("tconst");
                String nconst = record.get("nconst");
                String category = record.get("category");

                if (!movies.containsKey(tconst)) {continue;}
                if (category.equals("director")) {
                    movieToDirectors.computeIfAbsent(tconst, k -> new ArrayList<>()).add(nconst);
                    allNconsts.add(nconst);
                } else if (category.equals("actors") || category.equals("actress")) {
                    // return reference
                    List<String> topCast = movieToTopCast.computeIfAbsent(tconst, k-> new ArrayList<>());
                    if (topCast.size() < 3) {
                        topCast.add(nconst); // adds to the map value at the same time
                        allNconsts.add(nconst);
                    }
                }
            }
        }

        // dictonary for nconst to name lookup
        Map<String, String> nconstToName = new HashMap<>();
        try (InputStream is = Main.class.getClassLoader().getResourceAsStream("data/name.basics.tsv");
             Reader in = new InputStreamReader(is, StandardCharsets.UTF_8)) {

            Iterable<CSVRecord> records = CSVFormat.TDF
                    .withFirstRecordAsHeader()
                    .withQuote(null)
                    .parse(in);

            int topFive = 0;
            for (CSVRecord record : records) {
                String nconst = record.get("nconst");
                if (!allNconsts.contains(nconst)) {continue;}

                String primaryName = record.get("primaryName");
                nconstToName.put(nconst, primaryName);
            }
        }

        // add names of directors and topCasts to movie objects
        for (var entry : movies.entrySet()) {
            String tconst = entry.getKey();
            Movie m = entry.getValue();

            List<String> directorsList = movieToDirectors.get(tconst);
            if (directorsList != null) {
                for (String nconst : directorsList) {
                    String name = nconstToName.get(nconst);
                    if (name != null) {
                        m.directors.add(name);
                    }
                }
            }
            List<String> topCastList = movieToTopCast.get(tconst);
            if (topCastList != null) {
                for (String nconst : topCastList) {
                    String name = nconstToName.get(nconst);
                    if (name != null) {m.topCast.add(name);}
                }
            }
        }

        // write to csv
        File outputFile = new File("output/movies_clean.csv");
        outputFile.getParentFile().mkdirs();

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile));
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT
                     .withHeader("tconst", "title", "year", "genres", "rating", "numVotes","directors", "topCast"))) {

            int printed = 0;
            for (Movie movie : movies.values()) {
                if (movie.rating == null || movie.genre.equals("\\N")) { continue;}

                printer.printRecord(
                        sanitize(movie.tconst),
                        sanitize(movie.title),
                        sanitize(movie.year),
                        sanitize(movie.genre),
                        sanitize(movie.rating),
                        sanitize(movie.numVotes),
                        String.join(" | ", movie.directors),
                        String.join(" | ", movie.topCast)
                );
                printed++;
            }
            System.out.println("Total written to CSV: " + printed);
        }
        System.out.println("Cleaned CSV generated at: " + outputFile.getAbsolutePath());

    }
    private static String sanitize(String s) {
        return s == null ? "" : s.replaceAll("[\",]", "").trim();
    }

}
