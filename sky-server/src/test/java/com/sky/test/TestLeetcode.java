package com.sky.test;

import com.github.pagehelper.Page;

import java.util.*;

public class TestLeetcode {
    public static void main(String[] args) {
        new Solution().isPowerOfThree(2147483647);
    }
}

class Solution {
    public boolean isPowerOfThree(int n) {
        if (n == 1){
            return true;
        }
//        if (n < 0) {
//            n = -n;
//        }
        long num = 0,i = 1;
        while(n != num){
            num = (long) Math.pow(3,i);
            if (num > n){
                return false;
            }
            i++;
        }
        return true;
    }
}

//class Solution {
//    private static final int MOD = 1000000007;
//    public int[] productQueries(int n, int[][] queries) {
//        //将所有2次幂放入set中
//        List<Integer> power = new ArrayList<>();
//        int rep = 1;
//        while (n > 0){
//            if (n % 2 == 1){
//                power.add(rep);
//            }
//            n /= 2;
//            rep *= 2;
//        }
//
//        int m = power.size();
//        int[][] answers = new int[m][m];
//        for (int i = 0 ; i < m; i++ ){
//            long current = 1;
//            for (int j = i; j < m; j++) {
//                current = (current * power.get(j)) % MOD;
//                answers[i][j] = (int) current;
//            }
//        }
//
//        int[] result = new int[answers.length];
//        for (int i = 0; i < queries.length; i++) {
//            result[i] = answers[queries[i][0]][queries[i][1]];
//        }
//
//        return result;
//    }
//}

//// ... existing code ...
//class Solution {
//    public char kthCharacter(int k) {
//        StringBuilder word = new StringBuilder("a");
//        while (word.length() < k) {
//            int m = word.length();
//            for (int i = 0; i < m && word.length() < k; i++) {
//                char nextChar = (char) ((word.charAt(i) - 'a' + 1) % 26 + 'a');
//                word.append(nextChar);
//            }
//        }
//        return word.charAt(k - 1);
//    }
//}
// ... existing code ...

//class Solution {
//    public int numOfUnplacedFruits(int[] fruits, int[] baskets) {
//        int ans = 0;
//        for(int num : fruits){
//            for (int i = 0; i < baskets.length; i++) {
//                if (num <= baskets[i]){
//                    baskets[i] = 0;
//                    break;
//                }
//            }
//        }
//        for (int basket : baskets){
//            if (basket != 0){
//                ans++;
//            }
//        }
//        return ans;
//    }
//}
