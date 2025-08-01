package com.example.myrandomdishapp; 

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import org.json.JSONArray;
import org.json.JSONObject;

public class RecipeApiClient {

    // ★重要★ ここにあなたの楽天アプリケーションIDを設定してください。
    // 環境変数 'RAKUTEN_APP_ID' から読み込まれます。
    private static final String RAKUTEN_APP_ID = System.getenv("RAKUTEN_APP_ID");

    // 各APIのベースURL
    private static final String BASE_URL_CATEGORY_RANKING = "https://app.rakuten.co.jp/services/api/Recipe/CategoryRanking/20170426";
    private static final String BASE_URL_CATEGORY_LIST = "https://app.rakuten.co.jp/services/api/Recipe/CategoryList/20170426";

    // API呼び出し間の待機時間（ミリ秒）。楽天APIの利用制限（1秒1回）を遵守するため。
    private static final long REQUEST_INTERVAL_MS = 1500;

    // ロードしたカテゴリデータを保持するためのMap
    // キーはカテゴリID（String）、値はそのカテゴリのJSONObject
    private Map<String, JSONObject> mediumCategories; // 中カテゴリデータ
    private Map<String, JSONObject> smallCategories;  // 小カテゴリデータ

    public RecipeApiClient() {
        // アプリケーションIDが環境変数に設定されているかを確認
        if (RAKUTEN_APP_ID == null || RAKUTEN_APP_ID.isEmpty()) {
            System.err.println("エラー: 環境変数 'RAKUTEN_APP_ID' が設定されていません。");
            throw new IllegalStateException("楽天アプリケーションIDが設定されていません。アプリの環境変数設定を確認してください。");
        }
    }

    /**
     * 指定されたカテゴリIDのレシピランキングから料理名、画像URL、説明、材料、レシピURLのペアを取得します（最大4件）。
     *
     * @param categoryId レシピを取得したいカテゴリのID（例: "10-290-950" のように結合されたもの）
     * @return 料理名("recipeTitle")、画像URL("foodImageUrl")、説明("recipeDescription")、材料("recipeMaterial")、レシピURL("recipeUrl")
     * を含むMapのリスト。値がない場合は空文字列。
     * @throws Exception API呼び出しやJSON解析中に発生したエラー
     */
    public List<Map<String, String>> getDishAndImageAndDetailsFromCategoryRanking(String categoryId) throws Exception {
        List<Map<String, String>> recipesData = new ArrayList<>();
        String encodedCategoryId = URLEncoder.encode(categoryId, StandardCharsets.UTF_8.toString());

        String requestUrl = String.format("%s?applicationId=%s&categoryId=%s&format=json",
                                        BASE_URL_CATEGORY_RANKING, RAKUTEN_APP_ID, encodedCategoryId);

        System.out.println("APIリクエストURL (ランキング詳細): " + requestUrl); // デバッグ用ログ

        URL url;
        try {
            url = new URL(requestUrl);
        } catch (Exception e) {
            System.err.println("不正なURL形式です: " + requestUrl + " - " + e.getMessage());
            throw new RuntimeException("APIリクエストURLの構築に失敗しました。", e);
        }

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.connect();

        int responseCode = conn.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            String errorResponse = "";
            try (BufferedReader errIn = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = errIn.readLine()) != null) {
                    errorResponse += line;
                }
            } catch (Exception err) {
                System.err.println("エラーレスポンスの読み込み中にエラーが発生: " + err.getMessage());
            }
            throw new RuntimeException("APIリクエスト失敗: HTTP error code : " + responseCode + ", Response: " + errorResponse);
        }

        try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }

            JSONObject jsonResponse = new JSONObject(response.toString());

            // "result" キーが存在し、その値がJSON配列であることを確認
            if (jsonResponse.has("result") && jsonResponse.get("result") instanceof JSONArray) {
                JSONArray recipes = jsonResponse.getJSONArray("result"); // "result"直下の配列を取得
                for (int i = 0; i < recipes.length(); i++) {
                    JSONObject recipe = recipes.getJSONObject(i); // 各レシピオブジェクト
                    Map<String, String> recipeInfo = new HashMap<>();
                    
                    // 料理名を取得
                    if (recipe.has("recipeTitle")) {
                        recipeInfo.put("recipeTitle", recipe.getString("recipeTitle"));
                    } else {
                        recipeInfo.put("recipeTitle", "（料理名不明）");
                    }

                    // 画像URLを取得（foodImageUrlを優先、mediumImageUrlも試す）
                    if (recipe.has("foodImageUrl")) {
                        recipeInfo.put("foodImageUrl", recipe.getString("foodImageUrl"));
                    } else if (recipe.has("mediumImageUrl")) {
                        recipeInfo.put("foodImageUrl", recipe.getString("mediumImageUrl"));
                    } else {
                        recipeInfo.put("foodImageUrl", ""); // 画像URLがない場合は空文字列
                    }

                    // レシピの説明を取得
                    if (recipe.has("recipeDescription")) {
                        recipeInfo.put("recipeDescription", recipe.getString("recipeDescription"));
                    } else {
                        recipeInfo.put("recipeDescription", "説明はありません。");
                    }

                    // 材料のリストを取得し、カンマ区切りの文字列に変換
                    if (recipe.has("recipeMaterial") && recipe.get("recipeMaterial") instanceof JSONArray) {
                        JSONArray materialsArray = recipe.getJSONArray("recipeMaterial");
                        List<String> materialsList = new ArrayList<>();
                        for (int j = 0; j < materialsArray.length(); j++) {
                            materialsList.add(materialsArray.getString(j));
                        }
                        recipeInfo.put("recipeMaterial", String.join("、", materialsList)); // 材料を「、」区切りで結合
                    } else {
                        recipeInfo.put("recipeMaterial", "材料は不明です。");
                    }

                    // レシピURLを取得
                    if (recipe.has("recipeUrl")) {
                        recipeInfo.put("recipeUrl", recipe.getString("recipeUrl"));
                    } else {
                        recipeInfo.put("recipeUrl", ""); // URLがない場合は空文字列
                    }
                    
                    recipesData.add(recipeInfo);
                }
            } else {
                System.err.println("APIレスポンスの'result'キーが予期せぬ形式です: " + jsonResponse.toString());
            }
        } finally {
            conn.disconnect(); // HTTP接続を閉じる
        }
        return recipesData;
    }

    /**
     * カテゴリリストAPIを呼び出し、指定されたカテゴリタイプ（large, medium, small）のカテゴリデータを取得します。
     *
     * @param categoryType 取得したいカテゴリのタイプ ("large", "medium", "small")
     * @return カテゴリIDをキー、そのカテゴリのJSONObjectを値とするマップ
     * @throws Exception API呼び出し中のエラー
     */
    private Map<String, JSONObject> getCategoryDataByType(String categoryType) throws Exception {
        Map<String, JSONObject> categoryMap = new HashMap<>();
        String requestUrl = String.format("%s?applicationId=%s&categoryType=%s&format=json",
                                        BASE_URL_CATEGORY_LIST, RAKUTEN_APP_ID, categoryType);

        System.out.println("カテゴリリストAPIリクエストURL (" + categoryType + "): " + requestUrl); // デバッグ用ログ

        URL url;
        try {
            url = new URL(requestUrl);
        } catch (Exception e) {
            System.err.println("不正なURL形式です: " + requestUrl + " - " + e.getMessage());
            throw new RuntimeException("カテゴリリストAPIのURL構築に失敗しました。", e);
        }

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.connect();

        int responseCode = conn.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            String errorResponse = "";
            try (BufferedReader errIn = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = errIn.readLine()) != null) {
                    errorResponse += line;
                }
            } catch (Exception err) {
                System.err.println("エラーレスポンスの読み込み中にエラーが発生: " + err.getMessage());
            }
            throw new RuntimeException("カテゴリリストAPIリクエスト失敗: HTTP error code : " + responseCode + ", Response: " + errorResponse);
        }

        try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }

            JSONObject jsonResponse = new JSONObject(response.toString());

            if (jsonResponse.has("result") && jsonResponse.getJSONObject("result").has(categoryType)) {
                JSONArray categories = jsonResponse.getJSONObject("result").getJSONArray(categoryType);
                for (int i = 0; i < categories.length(); i++) {
                    JSONObject category = categories.getJSONObject(i);
                    // categoryId を Integer として取得し、String に変換
                    String categoryId = String.valueOf(category.getInt("categoryId"));
                    categoryMap.put(categoryId, category);
                }
            } else {
                System.err.println("カテゴリリストAPIレスポンスの'result'または'" + categoryType + "'キーが予期せぬ形式です: " + jsonResponse.toString());
            }
        } finally {
            conn.disconnect();
        }
        return categoryMap;
    }

    /**
     * アプリ起動時にMedium, Smallカテゴリデータをロードします。
     * LargeカテゴリはMediumカテゴリの親IDから取得するため、直接呼び出しは不要です。
     *
     * @throws Exception API呼び出し中のエラー
     */
    public void loadAllCategories() throws Exception {
        // Mediumカテゴリのロード
        mediumCategories = getCategoryDataByType("medium");
        TimeUnit.MILLISECONDS.sleep(REQUEST_INTERVAL_MS); // API呼び出し間隔を設ける

        // Smallカテゴリのロード
        smallCategories = getCategoryDataByType("small");
        TimeUnit.MILLISECONDS.sleep(REQUEST_INTERVAL_MS); // API呼び出し間隔を設ける

        System.out.println("Medium: " + mediumCategories.size() + ", Small: " + smallCategories.size() + " カテゴリのロードが完了しました。");
    }

    /**
     * 指定された小カテゴリIDから、そのカテゴリの完全なパスID（largeID-mediumID-smallID）を生成します。
     *
     * @param smallCategoryId 小カテゴリID
     * @return largeID-mediumID-smallID形式の文字列、または null（パスが見つからない場合）
     */
    public String getFullCategoryIdPath(String smallCategoryId) {
        // カテゴリデータがロードされているかを確認
        if (smallCategories == null || mediumCategories == null) {
            System.err.println("カテゴリデータがロードされていません（MediumまたはSmallがnull）。");
            return null;
        }

        JSONObject smallCat = smallCategories.get(smallCategoryId);
        if (smallCat == null) {
            System.err.println("指定された小カテゴリIDが見つかりません: " + smallCategoryId);
            return null;
        }

        // 小カテゴリの親カテゴリID（MediumカテゴリのID）を取得
        String mediumParentId = String.valueOf(smallCat.getInt("parentCategoryId"));
        JSONObject mediumCat = mediumCategories.get(mediumParentId);

        if (mediumCat == null) {
            System.err.println("中カテゴリ（親）が見つかりません: " + mediumParentId + " for small: " + smallCategoryId);
            return null;
        }

        // 中カテゴリの親カテゴリID（LargeカテゴリのID）を取得
        String largeParentId = String.valueOf(mediumCat.getInt("parentCategoryId"));
        // largeCat の JSONObject 自体は不要（IDのみ利用）

        // largeID-mediumID-smallID の形式で結合して返す
        return largeParentId + "-" + mediumParentId + "-" + smallCategoryId;
    }

    /**
     * ロードされた小カテゴリデータへのアクセスを提供します。
     * DishControllerから、ロード済みの小カテゴリIDリストを取得するために使用されます。
     *
     * @return ロードされた小カテゴリのMap<categoryId, JSONObject>、または null（未ロードの場合）
     */
    public Map<String, JSONObject> getSmallCategoryData() {
        return smallCategories;
    }

    /**
     * カテゴリデータがロード済みかどうかをチェックします。
     *
     * @return ロード済みであれば true
     */
    public boolean areCategoriesLoaded() {
        return mediumCategories != null && smallCategories != null;
    }

    /**
     * すべての小カテゴリから、ランキング上位の料理名を取得し、一つのセットにまとめます。
     * このメソッドは、すべての小カテゴリのランキングレシピを網羅的に取得する際に使用します。
     * (現在のアプリケーションの仕様では、このメソッドは直接利用されません)
     *
     * @return 取得したすべてのユニークな料理名のセット
     */
    public Set<String> getAllDishNamesFromAllCategories() {
        Set<String> allDishNames = new HashSet<>();
        List<String> smallCategoryIds = new ArrayList<>();

        try {
            // getSmallCategoryData() からカテゴリIDのみを抽出し、リストにする
            Map<String, JSONObject> smallCats = getSmallCategoryData();
            if (smallCats != null) {
                smallCategoryIds = new ArrayList<>(smallCats.keySet());
                Collections.shuffle(smallCategoryIds); // ランダム性を高めるためにシャッフル
            } else {
                System.err.println("getAllDishNamesFromAllCategories: Smallカテゴリデータがロードされていません。");
                return allDishNames;
            }
        } catch (Exception e) { // NullPointerExceptionの可能性も考慮しExceptionで捕捉
            System.err.println("getAllDishNamesFromAllCategories: 小カテゴリIDの取得中にエラーが発生しました: " + e.getMessage());
            e.printStackTrace();
            return allDishNames;
        }

        for (String categoryId : smallCategoryIds) {
            try {
                // 完全なカテゴリパスを生成
                String fullCategoryId = getFullCategoryIdPath(categoryId);
                if (fullCategoryId != null) {
                    // 料理名と画像のペアを取得するメソッドを呼び出す
                    List<Map<String, String>> recipesWithDetails = getDishAndImageAndDetailsFromCategoryRanking(fullCategoryId);
                    for (Map<String, String> recipe : recipesWithDetails) {
                        if (recipe.containsKey("recipeTitle")) {
                            allDishNames.add(recipe.get("recipeTitle"));
                        }
                    }
                    TimeUnit.MILLISECONDS.sleep(REQUEST_INTERVAL_MS); // API呼び出し間隔を設ける
                }
            } catch (Exception e) {
                System.err.println("getAllDishNamesFromAllCategories: カテゴリID " + categoryId + " からの料理名取得に失敗しました: " + e.getMessage());
            }
        }
        return allDishNames;
    }
}