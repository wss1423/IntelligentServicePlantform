package com.sky.test;


import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

/**
 * 使用POI来操作POI文件
 */
public class POITest {

    /**
     * 通过POI创建的Excel文件并写入文件内容
     */
    public static void write() throws Exception{
        //在内存中创建一个Excel文件
        XSSFWorkbook excel = new XSSFWorkbook();
        //在Excel 文件中创建一个sheet表
        XSSFSheet sheet = excel.createSheet("info");
        //在sheet中创建行对象
        //createRow("从第几行开始创建",)
        XSSFRow row = sheet.createRow(1);
        //创建单元格并且写入文件内容
        row.createCell(1).setCellValue("姓名");
        row.createCell(2).setCellValue("城市");

        //创建新行
        row = sheet.createRow(2);
        row.createCell(1).setCellValue("张三");
        row.createCell(2).setCellValue("北京");

        row = sheet.createRow(3);
        row.createCell(1).setCellValue("李四");
        row.createCell(2).setCellValue("南京");

        //通过输出流将内存中的Excel文件写入到磁盘
        FileOutputStream out = new FileOutputStream(new File("D:\\Program Files\\JetBrains\\info.xlsx"));
        excel.write(out);

        out.close();
        excel.close();
    }

    /**
     * 通过POI来读取Excel文件的内容
     */
    public static void read() throws Exception{
        FileInputStream in = new FileInputStream("D:\\Program Files\\JetBrains\\info.xlsx");
        //1.获取磁盘中已经存在的Excel文件
        XSSFWorkbook excel = new XSSFWorkbook(in);
        //2.读取Excel文件中的第一个sheet页
        XSSFSheet sheet = excel.getSheetAt(0);

        //获取sheet页中的最后一行的行号
        int lastRowNum = sheet.getLastRowNum();
        for (int i = 1; i <= lastRowNum; i++) {
            //获得某一行
            XSSFRow row = sheet.getRow(i);
            //获得单元格
            String cellValue1 = row.getCell(1).getStringCellValue();
            String cellValue2 = row.getCell(2).getStringCellValue();
            System.out.println(cellValue1 +  " " +cellValue2);
        }
        excel.close();
        in.close();

    }



    public static void main(String[] args) throws Exception{
        write();
        read();
    }


}
