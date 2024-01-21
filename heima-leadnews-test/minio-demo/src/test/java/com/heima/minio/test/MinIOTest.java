package com.heima.minio.test;

import com.heima.file.service.FileStorageService;
import com.heima.minio.MinIOApplication;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.FileInputStream;
import java.io.FileNotFoundException;

@SpringBootTest(classes = MinIOApplication.class)
@RunWith(SpringRunner.class)
public class MinIOTest {

    @Autowired
    private FileStorageService fileStorageService;

    @Test
    public void test() throws FileNotFoundException {
        FileInputStream fileInputStream = new FileInputStream("d:\\tmp\\list.html");
        String path = fileStorageService.uploadHtmlFile("", "list.html", fileInputStream);
        System.out.println(path);
    }






    /**
     * 把list.html文件上传到minio中，并且可以在浏览器中访问
     *
     * @param args
     */
/*    public static void main(String[] args) {
        try {
            FileInputStream fileInputStream = new FileInputStream("d:\\tmp\\list.html");
            // 1.获取minio的连接信息，创建一个minio的客户端
            MinioClient minioClient = MinioClient
                    .builder()
                    .credentials("minio", "minio123")
                    .endpoint("http://192.168.200.130:9000")
                    .build();
            // 2.上传
            PutObjectArgs putObjectArgs = PutObjectArgs
                    .builder()
                    .object("list.html")
                    .contentType("text/html")
                    .bucket("leadnews")
                    .stream(fileInputStream, fileInputStream.available(), -1)
                    .build();
            minioClient.putObject(putObjectArgs);
            // 访问路径
            System.out.println("http://192.168.200.130:9000/leadnews/list.html");
        } catch (Exception e) {
            e.printStackTrace();
        }

    }*/
}
