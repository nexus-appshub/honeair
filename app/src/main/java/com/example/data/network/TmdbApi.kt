package com.example.data.network

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TmdbApi {
    @GET("trending/all/day")
    suspend fun getTrendingToday(
        @Query("page") page: Int = 1,
        @Query("api_key") apiKey: String = "a359b11d9aa4c4803d25ef86cf7fb19c"
    ): TmdbResponse

    @GET("movie/now_playing")
    suspend fun getNowPlayingMovies(
        @Query("page") page: Int = 1,
        @Query("api_key") apiKey: String = "a359b11d9aa4c4803d25ef86cf7fb19c"
    ): TmdbResponse

    @GET("tv/on_the_air")
    suspend fun getOnTheAirTvShows(
        @Query("page") page: Int = 1,
        @Query("api_key") apiKey: String = "a359b11d9aa4c4803d25ef86cf7fb19c"
    ): TmdbResponse

    @GET("discover/movie")
    suspend fun getLatestReleasedMovies(
        @Query("sort_by") sortBy: String = "primary_release_date.desc",
        @Query("vote_count.gte") voteCountGte: Int = 5,
        @Query("page") page: Int = 1,
        @Query("api_key") apiKey: String = "a359b11d9aa4c4803d25ef86cf7fb19c"
    ): TmdbResponse

    @GET("discover/tv")
    suspend fun getLatestAiringAnime(
        @Query("with_genres") withGenres: String = "16",
        @Query("with_original_language") originalLanguage: String = "ja",
        @Query("sort_by") sortBy: String = "first_air_date.desc",
        @Query("vote_count.gte") voteCountGte: Int = 2,
        @Query("page") page: Int = 1,
        @Query("api_key") apiKey: String = "a359b11d9aa4c4803d25ef86cf7fb19c"
    ): TmdbResponse

    @GET("trending/movie/week")
    suspend fun getTrendingMovies(
        @Query("page") page: Int = 1,
        @Query("api_key") apiKey: String = "a359b11d9aa4c4803d25ef86cf7fb19c"
    ): TmdbResponse

    @GET("trending/tv/week")
    suspend fun getTrendingTvShows(
        @Query("page") page: Int = 1,
        @Query("api_key") apiKey: String = "a359b11d9aa4c4803d25ef86cf7fb19c"
    ): TmdbResponse

    @GET("discover/tv")
    suspend fun getAnime(
        @Query("with_genres") withGenres: String = "16",
        @Query("with_original_language") originalLanguage: String = "ja",
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("page") page: Int = 1,
        @Query("api_key") apiKey: String = "a359b11d9aa4c4803d25ef86cf7fb19c"
    ): TmdbResponse

    @GET("discover/movie")
    suspend fun getAnimeMovies(
        @Query("with_genres") withGenres: String = "16",
        @Query("with_original_language") originalLanguage: String = "ja",
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("page") page: Int = 1,
        @Query("api_key") apiKey: String = "a359b11d9aa4c4803d25ef86cf7fb19c"
    ): TmdbResponse

    @GET("discover/tv")
    suspend fun getKDramas(
        @Query("with_original_language") originalLanguage: String = "ko",
        @Query("without_genres") withoutGenres: String = "16",
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("page") page: Int = 1,
        @Query("api_key") apiKey: String = "a359b11d9aa4c4803d25ef86cf7fb19c"
    ): TmdbResponse

    @GET("discover/movie")
    suspend fun getActionMovies(
        @Query("with_genres") withGenres: String = "28",
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("page") page: Int = 1,
        @Query("api_key") apiKey: String = "a359b11d9aa4c4803d25ef86cf7fb19c"
    ): TmdbResponse

    @GET("discover/movie")
    suspend fun getSciFiMovies(
        @Query("with_genres") withGenres: String = "878",
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("page") page: Int = 1,
        @Query("api_key") apiKey: String = "a359b11d9aa4c4803d25ef86cf7fb19c"
    ): TmdbResponse

    @GET("discover/movie")
    suspend fun getHindiMovies(
        @Query("with_original_language") originalLanguage: String = "hi",
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("page") page: Int = 1,
        @Query("api_key") apiKey: String = "a359b11d9aa4c4803d25ef86cf7fb19c"
    ): TmdbResponse

    @GET("discover/tv")
    suspend fun getHindiSeries(
        @Query("with_original_language") originalLanguage: String = "hi",
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("page") page: Int = 1,
        @Query("api_key") apiKey: String = "a359b11d9aa4c4803d25ef86cf7fb19c"
    ): TmdbResponse

    @GET("discover/movie")
    suspend fun getHindiDubbedMovies(
        @Query("region") region: String = "IN",
        @Query("with_original_language") originalLanguage: String = "te|ta|en|kn|ml",
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("page") page: Int = 1,
        @Query("api_key") apiKey: String = "a359b11d9aa4c4803d25ef86cf7fb19c"
    ): TmdbResponse

    @GET("discover/tv")
    suspend fun getHindiDubbedKDramas(
        @Query("with_original_language") originalLanguage: String = "ko",
        @Query("without_genres") withoutGenres: String = "16",
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("vote_count.gte") voteCountGte: Int = 10,
        @Query("page") page: Int = 1,
        @Query("api_key") apiKey: String = "a359b11d9aa4c4803d25ef86cf7fb19c"
    ): TmdbResponse

    @GET("discover/movie")
    suspend fun getBengaliMovies(
        @Query("with_original_language") originalLanguage: String = "bn",
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("page") page: Int = 1,
        @Query("api_key") apiKey: String = "a359b11d9aa4c4803d25ef86cf7fb19c"
    ): TmdbResponse

    @GET("discover/tv")
    suspend fun getBengaliSeries(
        @Query("with_original_language") originalLanguage: String = "bn",
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("page") page: Int = 1,
        @Query("api_key") apiKey: String = "a359b11d9aa4c4803d25ef86cf7fb19c"
    ): TmdbResponse

    @GET("search/movie")
    suspend fun searchMovies(
        @Query("query") query: String,
        @Query("page") page: Int = 1,
        @Query("api_key") apiKey: String = "a359b11d9aa4c4803d25ef86cf7fb19c"
    ): TmdbResponse

    @GET("search/tv")
    suspend fun searchTvShows(
        @Query("query") query: String,
        @Query("page") page: Int = 1,
        @Query("api_key") apiKey: String = "a359b11d9aa4c4803d25ef86cf7fb19c"
    ): TmdbResponse

    @GET("{type}/{id}")
    suspend fun getMediaDetails(
        @Path("type") type: String, // "movie" or "tv"
        @Path("id") id: String,
        @Query("append_to_response") appendToResponse: String = "credits,videos",
        @Query("api_key") apiKey: String = "a359b11d9aa4c4803d25ef86cf7fb19c"
    ): TmdbMediaDetail

    @GET("tv/{id}/season/{season_number}")
    suspend fun getTvSeason(
        @Path("id") id: String,
        @Path("season_number") seasonNumber: Int,
        @Query("api_key") apiKey: String = "a359b11d9aa4c4803d25ef86cf7fb19c"
    ): TmdbSeasonDetail

    @GET("{type}/{id}/credits")
    suspend fun getCredits(
        @Path("type") type: String, // "movie" or "tv"
        @Path("id") id: String,
        @Query("api_key") apiKey: String = "a359b11d9aa4c4803d25ef86cf7fb19c"
    ): TmdbCredits

    @GET("find/{external_id}")
    suspend fun getByExternalId(
        @Path("external_id") externalId: String,
        @Query("external_source") externalSource: String = "imdb_id",
        @Query("api_key") apiKey: String = "a359b11d9aa4c4803d25ef86cf7fb19c"
    ): TmdbFindResponse
}

data class TmdbFindResponse(
    val movie_results: List<TmdbMediaResult>? = null,
    val tv_results: List<TmdbMediaResult>? = null
)

data class TmdbResponse(
    val results: List<TmdbMediaResult>?
)

data class TmdbMediaResult(
    val id: Int,
    val title: String?,
    val name: String?, // TV shows use 'name' instead of 'title'
    val poster_path: String?,
    val backdrop_path: String?,
    val vote_average: Double?,
    val release_date: String?,
    val first_air_date: String?,
    val overview: String?,
    val genre_ids: List<Int>? = null,
    val original_language: String? = null,
    val media_type: String? = null
)

data class TmdbMediaDetail(
    val id: Int,
    val title: String?,
    val name: String?,
    val overview: String?,
    val poster_path: String?,
    val backdrop_path: String?,
    val vote_average: Double?,
    val release_date: String?,
    val first_air_date: String?,
    val number_of_seasons: Int?,
    val number_of_episodes: Int?,
    val runtime: Int?,
    val episode_run_time: List<Int>?,
    val credits: TmdbCredits?,
    val videos: TmdbVideos? = null
)

data class TmdbVideos(
    val results: List<TmdbVideoResult>?
)

data class TmdbVideoResult(
    val id: String,
    val key: String?,
    val site: String?,
    val type: String?,
    val official: Boolean?
)

data class TmdbCredits(
    val cast: List<TmdbCast>?
)

data class TmdbCast(
    val id: Int,
    val name: String?,
    val profile_path: String?,
    val character: String?
)

data class TmdbSeasonDetail(
    val id: Int,
    val name: String?,
    val overview: String?,
    val poster_path: String?,
    val season_number: Int?,
    val episodes: List<TmdbEpisode>?
)

data class TmdbEpisode(
    val id: Int,
    val name: String?,
    val overview: String?,
    val episode_number: Int?,
    val season_number: Int?,
    val still_path: String?,
    val vote_average: Double?
)
