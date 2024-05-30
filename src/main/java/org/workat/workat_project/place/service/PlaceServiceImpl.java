package org.workat.workat_project.place.service;

import java.net.URLEncoder;
import java.text.DecimalFormat;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.workat.workat_project.picture.repository.PictureMapper;
import org.workat.workat_project.place.entity.PlaceDetailDTO;
import org.workat.workat_project.place.entity.PlaceListDTO;

import org.workat.workat_project.place.entity.PlaceVO;
import org.workat.workat_project.place.repository.PlaceMapper;

import lombok.RequiredArgsConstructor;
import org.workat.workat_project.review.entity.ReviewResDTO;
import org.workat.workat_project.review.service.ReviewService;
import org.workat.workat_project.room.entity.RoomResDTO;
import org.workat.workat_project.room.entity.RoomVO;
import org.workat.workat_project.room.repository.RoomMapper;
import org.workat.workat_project.room.service.RoomService;

@Service
@RequiredArgsConstructor
public class PlaceServiceImpl implements PlaceService {

    @Value("${naver.client-id}")
    private String clientId;
    @Value("${naver.client-secret}")
    private String clientSecret;

    private final PlaceMapper placeMapper;
    private final PictureMapper pictureMapper;
    private final RoomService roomService;
    private final ReviewService reviewService;

    @Override
    public List<PlaceListDTO> getMainViewPlaceList() {
        return placeMapper.getMainViewPlaceList();
    }

    @Override
    public PlaceDetailDTO getPlaceDetail(int placeId) {

        PlaceVO placeVO = placeMapper.getPlaceInfo(placeId);
        List<String> placePictureList = pictureMapper.getPlacePictureSources(placeId);

        //숙소(호텔) 관련 정보 입력
        PlaceDetailDTO placeDetailDTO = setLatLon(placeVO.getPlace_addr()); //네이버 api 사용하여 위도 경도 입력
        placeDetailDTO.setPlaceVO(placeVO);
        placeDetailDTO.setPlace_picture_source(placePictureList);

        //방(객실) 관련 정보 입력
        placeDetailDTO.setRoomList(roomService.getRoomInfoList(placeId));

        //리뷰 관련 정보 입력
        List<ReviewResDTO> reviewList = reviewService.getReviewInfoList(placeId);
        placeDetailDTO.setReviewList(reviewList);

        //별점 평균값 계산
        placeDetailDTO.setRating(calculateRating(reviewList));

        return placeDetailDTO;
    }

    public PlaceDetailDTO setLatLon(String address) {
        PlaceDetailDTO placeDetailDTO = new PlaceDetailDTO();
        try {
            String reqUrl = "https://naveropenapi.apigw.ntruss.com/map-geocode/v2/geocode?query=" + address;

            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-NCP-APIGW-API-KEY-ID", clientId);
            headers.set("X-NCP-APIGW-API-KEY", clientSecret);

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(reqUrl, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(response.getBody());
                JsonNode addresses = root.path("addresses");

                if (addresses.isArray() && !addresses.isEmpty()) {
                    JsonNode addressNode = addresses.get(0);
                    placeDetailDTO.setRoadAddr(addressNode.path("roadAddress").asText());
                    placeDetailDTO.setJibunAddr(addressNode.path("jibunAddress").asText());
                    placeDetailDTO.setLongitude(addressNode.path("x").asDouble());
                    placeDetailDTO.setLatitude(addressNode.path("y").asDouble());
                    return placeDetailDTO;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return placeDetailDTO;
    }

    public double calculateRating(List<ReviewResDTO> reviewList) {
        int totalRating = 0;
        int numReviews = 0;

        if (reviewList != null && !reviewList.isEmpty()) {
            for (ReviewResDTO review : reviewList) {
                if (review.getReviewVO() != null && review.getReviewVO().getRating() >= 0) {
                    totalRating += review.getReviewVO().getRating();
                    numReviews++;
                }
            }
        }
        double rating;
        if (numReviews > 0) {
            double averageRating = (double) totalRating / numReviews;
            DecimalFormat df = new DecimalFormat("#.#");
            rating = Double.parseDouble(df.format(averageRating));
        } else {
            rating = 0;
        }

        return rating;
    }
}