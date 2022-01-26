package manager;

import dao.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.Socket;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;

public class MainCateManager {
    public static void main(String[] args) {
        MainCateManager mcm = new MainCateManager();
//          mcm.mainCate();
//          mcm.subCate();
//          mcm.detailCate();
//            mcm.weekBestLink();
        mcm.getBookInfo();
    }

    public void mainCate() {
        try {
            BookDAO dao = new BookDAO();
            Document doc = Jsoup.connect("http://www.yes24.com/main/default.aspx").get();
            Elements maincate = doc.select("li.quickCate_dept1 a.qTit");
            Elements link = doc.select("div.quickCateSub dt a");
            String[] cate = maincate.text().split(" ");
            MainCategoryVO vo = new MainCategoryVO();
            for (int i = 0; i < cate.length; i++) { // ebook까지만 긁어올꺼면 i<3으로 설정
                vo.setId(i + 1);
                vo.setName(cate[i]);
                vo.setLink(link.get(i).attr("href"));

                dao.InsertMainCate(vo);
            }
            System.out.println("오라클에 저장 완료!!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void subCate() { // 국내도서, 외국도서카테고리 받아오기
        try {
            BookDAO dao = new BookDAO();
            List<MainCategoryVO> list = dao.MainCateList();
            SubCategoryVO vo = new SubCategoryVO();
            for (int i = 0; i < 2; i++) { // for(int i=0;i< list.size();i++) => 전체 다 긁어 올 시
                System.out.println(list.get(i).getLink());
                Document doc = Jsoup.connect(list.get(i).getLink()).get();
                Elements subcate = doc.select("div.CateLiArea li.cate2d a.lnk_cate2d");

                for (int j = 0; j < subcate.size(); j++) {
                    System.out.println(subcate.get(j).text());
                    vo.setMainid(list.get(i).getId());
                    vo.setName(subcate.get(j).text());
                    vo.setLink(subcate.get(j).attr("href"));

                    dao.InsertSubCate(vo);
                }
            }
            System.out.println("오라클에 저장 완료!!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /* subCategory에서 링크를 받아서 주간베스트링크를 뿌려준다 */
    public List<String> weekBestLink() {
        List<String> weekBestLink = new ArrayList<>();
        try {
            BookDAO dao = new BookDAO();
            List<SubCategoryVO> list = dao.SubCateList();
            int no =0;
            System.out.println("========== 주간베스트 링크 출력 ==========");
            for (int i = 0; i < list.size(); i++) {
                Document subCateDoc = Jsoup.connect(list.get(i).getLink()).get(); // subCategory에서 link를 가져온다
                Element bestLink = subCateDoc.selectFirst("div.cateTit_sub span a"); // 주간베스트 들어가기위한 링크
                System.out.println(bestLink.text());
                int endPageNo = getLastPage(bestLink);
                if(!(bestLink.text().equals("주간베스트")) || endPageNo<=6) { // 주간베스트가 없는 sub_Category일 경우 || 주간베스트 page가 5이하인 경우
                    System.out.println("주간베스트 없거나 적음 : " + list.get(i).getName());
                    for (int k = 1; k <= 20; k++) { // 20page만 가져온다. (최대 페이지로 받아올 경우 너무 많은 양이 들어옴)
                        weekBestLink.add(list.get(i).getLink() + "?PageNumber=" + k);
                        System.out.println(weekBestLink.get(no++));
                    }
                } else {
                    System.out.print(list.get(i).getName() + " : ");
                    System.out.println(bestLink.attr("href"));

                    for (int j = 0; j < endPageNo; j++) { // 테스트를 위해 10으로 설정, 실제 데이터를 받아올때는 j<end로 변경
                        weekBestLink.add("http://www.yes24.com" + bestLink.attr("href") + "&PageNumber=" + (j + 1));
                        System.out.println(weekBestLink.get(no++));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return weekBestLink;
    }


    /* 주간 베스트의 마지막 페이지를 얻는 메소드 */
    public int getLastPage(Element bestLink){
        int lasgPage=0;
        try {
            Document bestDoc = Jsoup.connect("http://www.yes24.com" + bestLink.attr("href")).get(); //해당 subCategory의 주간베스트
            Element endPage = bestDoc.selectFirst("div.yesUI_pagenS a.bgYUI.end"); // 마지막 페이지가 나와있는 곳
            lasgPage = Integer.parseInt(endPage.attr("href").substring(endPage.attr("href").lastIndexOf('=') + 1).trim()); // 마지막 페이지 번호
            System.out.println("마지막 페이지 : " + lasgPage);
        }catch (Exception e){
            e.printStackTrace();
        }
        return lasgPage;
    }


    // 세부적인 카테고리에서 받아옴(commit : 119fe158e42374806851c4f4186f55cc0362c596)
    // 현재 코드 : 주간베스트로 진입하여 데이터 긁어옴 : 2022/01/26 국내, 외국도서 완료
    public void getBookInfo() {
        try {
            BookDAO dao = new BookDAO();
            BooksVO bvo = new BooksVO();
            List<String> weekBestList = weekBestLink();
            // weekBestLink()의 for문의 i 초기값을 바꾸면 일치하지 않는 문제 해결해야함.
            int cateid=0;
            for (int j = 0; j < weekBestList.size(); j++) {
                Document bookDoc = Jsoup.connect(weekBestList.get(j)).get();
                Elements bookLink = bookDoc.select("span.imgBdr a"); // 주간베스트에 출력된 책 링크
                if(weekBestList.get(j).substring(weekBestList.get(j).lastIndexOf('=')+1).equals("1"))
                    cateid++;
                for (int k = 0; k < bookLink.size(); k++) {
                    try {
                        Document doc2 = Jsoup.connect("http://www.yes24.com" + bookLink.get(k).attr("href")).get();
                        bvo.setCateid(cateid);
                        System.out.println("카테고리 id : "+bvo.getCateid());
                        // 포스터
                        try {
                            Element poster = doc2.selectFirst("div.gd_imgArea img");
                            System.out.println("포스터 : " + poster.attr("src"));
                            bvo.setPoster(poster.attr("src"));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        // 책 제목
                        Element title = doc2.selectFirst("div.gd_titArea h2.gd_name");
                        System.out.println("책 제목 : " + title.text());
                        bvo.setName(title.text());

                        // 부가 제목 (추가 미정)

                        // 저자
                        try {
                            Element author = doc2.selectFirst("span.gd_auth");
                            System.out.println("저자 : " + author.text());
                            bvo.setAuthor(author.text());
                        } catch (Exception e) {
                            // Me Reader &amp; 8 books Library : Peppa Pig 페파 피그 미리더 사운드북 저자는 Peppa pig
                            System.out.println("출판사 저");
                            bvo.setAuthor("출판사 저");
                        }

                        // 출판사
                        Element publisher = doc2.selectFirst("span.gd_pub ");
                        System.out.println("출판사 : " + publisher.text());
                        bvo.setPublisher(publisher.text());

                        // 출간일(1안) Date로 바꾸기 위해 yyyy-MM-dd  형식으로 바꿈
                        Element regdate = doc2.selectFirst("span.gd_date");
                        String[] s = regdate.text().split(" ");
                        String date = s[0].substring(0, s[0].lastIndexOf('년')) + "-" +
                                s[1].substring(0, s[1].lastIndexOf('월')) + "-" +
                                s[2].substring(0, s[2].lastIndexOf('일'));
                        System.out.println("출간일 : " + date);
                        bvo.setRegdate(Date.valueOf(date));

                        // 평점
                        try {
                            Element score = doc2.selectFirst("span.gd_rating em.yes_b");
                            System.out.println("평점 : " + score.text());
                            bvo.setScore(Double.parseDouble(score.text()));
                        } catch (Exception e) {
                            System.out.println("첫번째 구매리뷰를 남겨주세요");
                            bvo.setScore(0.0);
                        }

                        // 판매 상태
                        Element status = doc2.selectFirst("p.gd_saleState");
                        System.out.println("판매 상태 : " + status.text());
                        bvo.setStatus(status.text());


                        //가격 (정가
                        Element price = doc2.selectFirst("div.gd_infoTb span");
                        System.out.println("정가 : " + price.text());
                        bvo.setPrice(price.text()); //String으로 문자로 삽입

                        // 할인율
                        try {
                            Element salesrate = doc2.selectFirst("tr.accentRow td");
                            String rate = salesrate.text().substring(salesrate.text().indexOf('(') + 1, salesrate.text().lastIndexOf('%'));
                            System.out.println("할인율 : " + rate + "%");
                            bvo.setSalerate(Integer.parseInt(rate)); //int로 숫자로 삽입
                        } catch (Exception e) {
                            System.out.println("할인 없음");
                            bvo.setSalerate(0);
                        }

                        // 배송비 (1안)
                        try {
                            Element shipprice = doc2.selectFirst("li.deli_des"); // 출력결과 => 배송비 : 무료 배송비 안내
                            String ship = shipprice.text().substring(shipprice.text().indexOf(':') + 1).trim();
                            Element shiptrim = doc2.selectFirst("li.deli_des a"); // 배송비 안내 자르기 위해 추출
                            ship = ship.replaceAll(shiptrim.text(), " ").trim();
                            System.out.println("배송비 : " + ship);
                            bvo.setShipprice(ship);
                        } catch (Exception e) {
                            System.out.println("기본배송비 2000원"); // 기본 배송비 설정해야함
                            bvo.setShipprice("2000원");
                        }

                        // 출고 예정일(어려움), javascript로 작성되어 있음

                        // 책 쪽수, 무게, 크기 (추가 미정)
                        // 출간일, 책(쪽수,무게,크기) , ISBN13, 배송비
                        Elements bookinfo = doc2.select("table.tb_nor td.lastcol");

                        // 출간일(2안)
                        /*String[] s2 = bookinfo.get(0).text().split(" ");
                        String date2 = s2[0].substring(0, s2[0].lastIndexOf('년')) + "-" +
                                s2[1].substring(0, s2[1].lastIndexOf('월')) + "-" +
                                s2[2].substring(0, s2[2].lastIndexOf('일'));
                        System.out.println("출간일 : " + date2);*/
//                    bvo.setRegdate(Date.valueOf(date2));

                        // 책(쪽수,무게,크기)
                        String booksize = bookinfo.get(1).text().substring(bookinfo.get(1).text().indexOf('기') + 1).trim();
                        String[] size = booksize.split("\\|");
                        for (String z : size)
                            System.out.println("책 쪽수 : " + z.trim());

                        // ISBN13
                        String isbn = bookinfo.get(2).text().replaceAll("ISBN13", "").trim();
                        System.out.println("ISBN : " + isbn);
                        bvo.setIsbn(isbn);

                        // 배송비(1안 사용 : 없는 책 존재)
                        /*String shipprice2 = bookinfo.get(4).text().substring(bookinfo.get(4).text().lastIndexOf(':') + 1).trim();
                        System.out.println(shipprice2);
//                      bvo.setShipprice(shipprice2);*/

                        //태그 (추가 미정)
                        try {
                            Element tag = doc2.selectFirst("div.gd_tagArea");
                            System.out.println("태그 : " + tag.text());
                        } catch (NullPointerException e) {
                            System.out.println("태그 없음");
                        }

                        // 책 소개
                        try {
                            Element content = doc2.selectFirst("textarea.txtContentText");
                            /* HTML태그들이 포함 되어있어 삭제하기위해 replaceAll() 사용함 */
                            System.out.println("책 소개 : " + content.text().replaceAll("<.*[^가-힣]*.>", "").replace("\n", "").trim());
                            bvo.setContent(content.text().replaceAll("<.*[^가-힣]*.>", "").replace("\n", "").trim());
                        } catch (Exception e) {
                            // 책 소개가 없는 것은 출판사 리뷰가 있는 것도 있는데 추가할지느 미정.
                            System.out.println("");
                            bvo.setContent("");
                        }
                    }catch (Exception e){
                        System.out.println("19세 오류");
                        break;
                    }
                    // 목차 (추가 미정)

                    System.out.println("===========================================================================================================================");
//                    dao.InsertBook(bvo);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
    // 더 세부적인 카테고리(여기 까지는 미정)
    /*public void detailCate() {
        try {
            BookDAO dao = new BookDAO();
            List<MainCategoryVO> mList = dao.MainCateList();
            DetailCategoryVO vo = new DetailCategoryVO();
//            System.out.println(mList.get(0).getLink());

            int num = 0;  //subID 외래키를 위해 설정
            for (int i = 0; i < 2; i++) {
                Document doc = Jsoup.connect(mList.get(i).getLink()).get();
                for (int j = 0; j < dao.SubCateCount(i + 1); j++) {
                    Elements decate = doc.select("div#mCateLi_00" + j + " li a");
                    for (int k = 0; k < decate.size(); k++) {
                        System.out.println(decate.get(k).text());
                        System.out.println(decate.get(k).attr("href"));
                        if(i!=0) {
                            num += dao.SubCateCount(i);
                        }
                        System.out.println(j+num+1);
                        vo.setSubid(j+num+1);
                        vo.setName(decate.get(k).text());
                        vo.setLink(decate.get(k).attr("href"));

                        dao.InsertDetailCate(vo);
                    }
                }
            }
            System.out.println("오라클 저장 완료!!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }*/
