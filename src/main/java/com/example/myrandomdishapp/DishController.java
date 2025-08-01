package com.example.myrandomdishapp;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.json.JSONObject;

@Controller
public class DishController {

    private final RecipeApiClient apiClient;
    private Random random;
    private ScheduledExecutorService executor;

    private List<String> availableSmallCategoryIds;
    private List<String> currentDisplayMessage;

    public DishController() {
        this.apiClient = new RecipeApiClient();
        this.random = new Random();
        this.executor = Executors.newSingleThreadScheduledExecutor();

        loadAllCategoryData();
        this.currentDisplayMessage = Collections.singletonList("カテゴリデータをロード中...");
    }

    private void loadAllCategoryData() {
        executor.schedule(() -> {
            try {
                apiClient.loadAllCategories();

                if (apiClient.areCategoriesLoaded()) {
                    Map<String, JSONObject> smallCats = apiClient.getSmallCategoryData();
                    if (smallCats != null && !smallCats.isEmpty()) {
                        this.availableSmallCategoryIds = new ArrayList<>(smallCats.keySet());
                        Collections.shuffle(this.availableSmallCategoryIds);
                        System.out.println("全カテゴリデータのロードが完了しました。小カテゴリ数: " + this.availableSmallCategoryIds.size());
                        this.currentDisplayMessage = Collections.singletonList("ボタンを押して献立をゲット！");
                    } else {
                        System.err.println("小カテゴリデータが空または取得できませんでした。");
                        this.currentDisplayMessage = Collections.singletonList("カテゴリデータ取得失敗。");
                    }
                } else {
                    System.err.println("カテゴリデータが完全にロードされませんでした。");
                    this.currentDisplayMessage = Collections.singletonList("カテゴリロードエラー。");
                }
            } catch (Exception e) {
                System.err.println("カテゴリデータのロード中にエラーが発生しました: " + e.getMessage());
                e.printStackTrace();
                this.currentDisplayMessage = Collections.singletonList("カテゴリロードエラー。詳細: " + e.getMessage());
            }
        }, 0, TimeUnit.SECONDS);
    }

    @GetMapping("/")
    public String showDishPage(Model model) {
        model.addAttribute("dishName", currentDisplayMessage.get(0));
        return "index";
    }

    @PostMapping("/api/generateDish")
    @ResponseBody
    public ResponseEntity<Map<String, String>> generateDishApi() {
        Map<String, String> response = new HashMap<>();

        if (availableSmallCategoryIds == null || availableSmallCategoryIds.isEmpty() || currentDisplayMessage.get(0).contains("ロード中") || currentDisplayMessage.get(0).contains("エラー")) {
            response.put("dishName", currentDisplayMessage.get(0));
            response.put("dishImageUrl", "");
            response.put("recipeDescription", "");
            response.put("recipeMaterial", "");
            response.put("recipeUrl", ""); // レシピURLも空で返す
            return new ResponseEntity<>(response, HttpStatus.SERVICE_UNAVAILABLE);
        }

        try {
            String selectedSmallCategoryId = availableSmallCategoryIds.get(random.nextInt(availableSmallCategoryIds.size()));
            String fullCategoryIdForRanking = apiClient.getFullCategoryIdPath(selectedSmallCategoryId);

            if (fullCategoryIdForRanking != null) {
                List<Map<String, String>> recipesData = apiClient.getDishAndImageAndDetailsFromCategoryRanking(fullCategoryIdForRanking);

                if (recipesData != null && !recipesData.isEmpty()) {
                    Map<String, String> selectedRecipe = recipesData.get(random.nextInt(recipesData.size()));
                    String finalDishName = selectedRecipe.get("recipeTitle");
                    String finalDishImageUrl = selectedRecipe.get("foodImageUrl");
                    String finalDescription = selectedRecipe.get("recipeDescription");
                    String finalMaterials = selectedRecipe.get("recipeMaterial");
                    String finalRecipeUrl = selectedRecipe.get("recipeUrl"); // レシピURLを取得

                    response.put("dishName", finalDishName);
                    response.put("dishImageUrl", finalDishImageUrl != null ? finalDishImageUrl : "");
                    response.put("recipeDescription", finalDescription != null ? finalDescription : "説明はありません。");
                    response.put("recipeMaterial", finalMaterials != null ? finalMaterials : "材料は不明です。");
                    response.put("recipeUrl", finalRecipeUrl != null ? finalRecipeUrl : ""); // レシピURLをレスポンスに含める
                    
                    System.out.println("選ばれた献立 (API): " + finalDishName + " (完全カテゴリID: " + fullCategoryIdForRanking + ", 画像URL: " + finalDishImageUrl + ", URL: " + finalRecipeUrl + ")");
                    return new ResponseEntity<>(response, HttpStatus.OK);
                } else {
                    response.put("dishName", "このカテゴリ（" + fullCategoryIdForRanking + "）には料理がありませんでした。もう一度！");
                    response.put("dishImageUrl", "");
                    response.put("recipeDescription", "");
                    response.put("recipeMaterial", "");
                    response.put("recipeUrl", "");
                    System.err.println("カテゴリID " + fullCategoryIdForRanking + " からレシピが取得できませんでした。");
                    return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
                }
            } else {
                response.put("dishName", "カテゴリパス生成エラー。もう一度！");
                response.put("dishImageUrl", "");
                response.put("recipeDescription", "");
                response.put("recipeMaterial", "");
                response.put("recipeUrl", "");
                System.err.println("完全なカテゴリパスを生成できませんでした: " + selectedSmallCategoryId);
                return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
            }
        } catch (Exception e) {
            System.err.println("料理名の生成中にエラーが発生しました (API): " + e.getMessage());
            e.printStackTrace();
            response.put("dishName", "料理生成中にエラーが発生しました。");
            response.put("dishImageUrl", "");
            response.put("recipeDescription", "");
            response.put("recipeMaterial", "");
            response.put("recipeUrl", "");
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}