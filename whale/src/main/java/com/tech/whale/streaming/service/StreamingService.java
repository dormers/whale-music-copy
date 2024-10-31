package com.tech.whale.streaming.service;

import com.neovisionaries.i18n.CountryCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.exceptions.SpotifyWebApiException;
import se.michaelthelin.spotify.model_objects.specification.*;
import se.michaelthelin.spotify.requests.data.personalization.simplified.GetUsersTopTracksRequest;
import org.apache.hc.core5.http.ParseException;

import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonArray;
import com.google.gson.JsonPrimitive;
import com.tech.whale.streaming.models.StreamingDao;
import com.tech.whale.streaming.models.TrackDto;
import se.michaelthelin.spotify.requests.data.search.simplified.SearchTracksRequest;
import se.michaelthelin.spotify.requests.data.artists.GetArtistsTopTracksRequest;
import se.michaelthelin.spotify.requests.data.artists.GetArtistsAlbumsRequest;


@Service
public class StreamingService {

    @Autowired
    private SpotifyApi spotifyApi;
    
    @Autowired
    private StreamingDao streamingDao;

    // Spotify API 초기화 메서드
    private void initializeSpotifyApi(HttpSession session) {
        String accessToken = (String) session.getAttribute("accessToken");
        String refreshToken = (String) session.getAttribute("refreshToken");

        if (accessToken == null || refreshToken == null) {
            System.out.println("accessToken 또는 refreshToken이 세션에 없습니다.");
            return;
        }

        this.spotifyApi = new SpotifyApi.Builder()
                .setAccessToken(accessToken)
                .setRefreshToken(refreshToken)
                .build();
    }

    // Access Token 갱신 메서드
    public void refreshAccessToken(HttpSession session) {
        try {
            var authorizationCodeCredentials = spotifyApi.authorizationCodeRefresh().build().execute();
            String newAccessToken = authorizationCodeCredentials.getAccessToken();

            // 세션에 새로운 accessToken 저장
            session.setAttribute("accessToken", newAccessToken);
            spotifyApi.setAccessToken(newAccessToken);

            System.out.println("새로운 accessToken: " + newAccessToken);
        } catch (IOException | SpotifyWebApiException | ParseException e) {
            System.out.println("Access Token 갱신 실패: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Access Token 유효성 검사 메서드
    private boolean isTokenValid(HttpSession session) {
        initializeSpotifyApi(session);

        try {
            spotifyApi.getCurrentUsersProfile().build().execute();
            return true;
        } catch (SpotifyWebApiException | IOException | ParseException e) {
            System.out.println("유효하지 않은 Access Token입니다. 갱신을 시도합니다.");
            refreshAccessToken(session);
            return false;
        }
    }

    // Top 트랙 가져오기 비동기 메서드
    public CompletableFuture<Paging<Track>> getUsersTopTracksAsync(HttpSession session) {
        initializeSpotifyApi(session);

        GetUsersTopTracksRequest request = spotifyApi.getUsersTopTracks()
                .limit(10)
                .time_range("medium_term")
                .build();

        return request.executeAsync().exceptionally(e -> {
            System.out.println("예상치 못한 오류 발생: " + e.getMessage());
            return null;
        });
    }

    // 음악 재생 메서드
    public boolean playTrack(HttpSession session, String trackId) {
        initializeSpotifyApi(session);

        try {
            JsonArray uris = new JsonArray();
            uris.add(new JsonPrimitive("spotify:track:" + trackId));

            var playRequest = spotifyApi.startResumeUsersPlayback()
                    .uris(uris);

            // 재생 위치가 저장되어 있다면 해당 위치에서 재생 시작
            if (currentPositionMs != null) {
                playRequest = playRequest.position_ms(currentPositionMs);
            }

            playRequest.build().execute();

            // 재생이 시작되면 위치 초기화
            currentPositionMs = null;
            return true;
        } catch (IOException | SpotifyWebApiException | ParseException e) {
            System.out.println("Failed to play track: " + e.getMessage());
            return false;
        }
    }

    // 현재 재생 위치를 저장할 변수
    private Integer currentPositionMs = null;

    // 음악 일시정지 메서드 추가
    public boolean pauseTrack(HttpSession session) {
        initializeSpotifyApi(session);

        try {
            // 현재 재생 상태를 가져와 재생 위치 저장
            var playbackState = spotifyApi.getInformationAboutUsersCurrentPlayback().build().execute();
            if (playbackState != null && playbackState.getProgress_ms() != null) {
                currentPositionMs = playbackState.getProgress_ms();
            }

            spotifyApi.pauseUsersPlayback().build().execute();
            return true;
        } catch (IOException | SpotifyWebApiException | ParseException e) {
            System.out.println("Failed to pause track: " + e.getMessage());
            return false;
        }
    }

    // 트랙 세부정보 가져오는 메서드
    public Track getTrackDetail(HttpSession session, String trackId) {
        initializeSpotifyApi(session);

        try {
            // Spotify API에서 트랙 상세 정보 가져오기
            return spotifyApi.getTrack(trackId).build().execute();
        } catch (IOException | SpotifyWebApiException | ParseException e) {
            System.out.println("Failed to fetch track details: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    // 앨범 세부정보 가져오는 메서드
    public Album getAlbumDetail(HttpSession session, String albumId) {
        initializeSpotifyApi(session);

        try {
            // Album 정보 가져오기
            return spotifyApi.getAlbum(albumId).build().execute();
        } catch (IOException | SpotifyWebApiException | ParseException e) {
            System.out.println("Failed to fetch album details: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    // 아티스트 세부정보 가져오는 메서드
    public Artist getArtistDetail(HttpSession session, String artistId) {
        initializeSpotifyApi(session);

        try {
            // 아티스트 정보 가져오기
            return spotifyApi.getArtist(artistId).build().execute();
        } catch (IOException | SpotifyWebApiException | ParseException e) {
            System.out.println("Failed to fetch artist details: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    // 아티스트의 상위 곡 가져오기
    public Track[] getArtistTopTracks(HttpSession session, String artistId) {
        initializeSpotifyApi(session);
        try {
            GetArtistsTopTracksRequest request = spotifyApi.getArtistsTopTracks(artistId, CountryCode.KR).build();
            return request.execute();
        } catch (IOException | SpotifyWebApiException | ParseException e) {
            System.out.println("Failed to fetch artist's top tracks: " + e.getMessage());
            return null;
        }
    }

    // 아티스트의 앨범 목록 가져오기
    public Paging<AlbumSimplified> getArtistAlbums(HttpSession session, String artistId) {
        initializeSpotifyApi(session);
        try {
            GetArtistsAlbumsRequest request = spotifyApi.getArtistsAlbums(artistId).limit(10).build();
            return request.execute();
        } catch (IOException | SpotifyWebApiException | ParseException e) {
            System.out.println("Failed to fetch artist's albums: " + e.getMessage());
            return null;
        }
    }

    // 연관된 아티스트 가져오기
    public Artist[] getRelatedArtists(HttpSession session, String artistId) {
        initializeSpotifyApi(session);
        try {
            return spotifyApi.getArtistsRelatedArtists(artistId).build().execute();
        } catch (IOException | SpotifyWebApiException | ParseException e) {
            System.out.println("Failed to fetch related artists: " + e.getMessage());
            return null;
        }
    }

    // Spotify에서 트랙 검색
    public Paging<Track> searchTracks(HttpSession session, String query) {
        initializeSpotifyApi(session);

        try {
            // 검색 요청 생성
            SearchTracksRequest searchTracksRequest = spotifyApi.searchTracks(query)
                    .limit(10)  // 최대 50개까지 가져올 수 있음
                    .build();

            // 결과 반환
            return searchTracksRequest.execute();
        } catch (IOException | SpotifyWebApiException | ParseException e) {
            System.out.println("Failed to search tracks: " + e.getMessage());
            return null;
        }
    }

//    -----------------------------------------------------------------------------------------------------
    
    // 데이터 베이스에 해당 트랙의 정보 입력 유무 확인 및 프라이머리 키 및 DTO 리턴
    public Integer selectTrackIdService(String track_artist, String track_name, String track_album, String track_cover, String trackSpotifyId) {
    	Integer trackId = streamingDao.selectTrackId(trackSpotifyId);
    	
    	if(trackId != null) {return trackId;}
    	else {
    		streamingDao.insertTrack(track_artist, track_name, track_album, track_cover, trackSpotifyId);
    		trackId = streamingDao.selectTrackId(trackSpotifyId);
    		return trackId;
    	}
    }
    
    public TrackDto selectTrackDtoService(Integer trackId) {
    	TrackDto trackDto = streamingDao.selectTrackDto(trackId);
    	return trackDto;
    }

}
