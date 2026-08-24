package io.morgaroth.media.library.storage

import com.typesafe.scalalogging.LazyLogging
import io.morgaroth.media.library.storage.TrackStatus
import zio.*

import java.sql.{Connection, PreparedStatement, ResultSet, Timestamp}
import java.time.{ZoneOffset, ZonedDateTime}
import java.util.UUID
import javax.sql.DataSource

class TracksStoragePostgres(ds: DataSource) extends ZioTracksStorageService with LazyLogging:

  private def withConnection[A](f: Connection => A): Task[A] =
    ZIO.attempt:
      val conn = ds.getConnection
      try f(conn)
      finally conn.close()

  private def logQuery(sql: String, params: Seq[Any] = Seq.empty): Unit =
    if params.isEmpty then logger.debug(s"SQL: $sql")
    else logger.debug(s"SQL: $sql | params: ${params.mkString(", ")}")

  private def readTrack(rs: ResultSet): Track =
    val playlists =
      Option(rs.getArray("playlists"))
        .map(_.getArray.asInstanceOf[Array[String]].toSet)
        .getOrElse(Set.empty)
    Track(
      url = rs.getString("url"),
      title = rs.getString("title"),
      artist = rs.getString("artist"),
      album = rs.getString("album"),
      startAt = Option(rs.getString("start_at")),
      endAt = Option(rs.getString("end_at")),
      fadeOutSeconds = Option(rs.getObject("fade_out_seconds")).map(_.asInstanceOf[Int]),
      volumeChange = Option(rs.getBigDecimal("volume_change")).map(v => BigDecimal(v)),
      status = TrackStatus.byDbRepr.getOrElse(rs.getString("status"), TrackStatus.Draft),
      idCheck = Option(rs.getString("id_check")),
      playlists = playlists,
      rawTitle = rs.getString("raw_title"),
      rawDescription = rs.getString("raw_description"),
      updatedAt = rs.getTimestamp("updated_at").toInstant.atZone(ZoneOffset.UTC),
      createdAt = rs.getTimestamp("created_at").toInstant.atZone(ZoneOffset.UTC),
      _id = UUID.fromString(rs.getString("id")),
    )

  override def getById(id: UUID): Task[Track] = withConnection: conn =>
    val sql = "SELECT * FROM tracks WHERE id = ?"
    logQuery(sql, Seq(id))
    val ps = conn.prepareStatement(sql)
    ps.setObject(1, id)
    val rs = ps.executeQuery()
    if rs.next() then readTrack(rs)
    else throw TrackNotFound(s"by id $id")

  override def save(document: Track): Task[Unit] = withConnection: conn =>
    val sql = """INSERT INTO tracks (id, url, title, artist, album, start_at, end_at, fade_out_seconds,
      |  volume_change, status, id_check, playlists, raw_title, raw_description, created_at, updated_at)
      |VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""".stripMargin
    logQuery(sql, Seq(document._id, document.url, document.title, document.artist))
    val ps = conn.prepareStatement(sql)
    ps.setObject(1, document._id)
    ps.setString(2, document.url)
    ps.setString(3, document.title)
    ps.setString(4, document.artist)
    ps.setString(5, document.album)
    ps.setString(6, document.startAt.orNull)
    ps.setString(7, document.endAt.orNull)
    document.fadeOutSeconds match
      case Some(v) => ps.setInt(8, v)
      case None => ps.setNull(8, java.sql.Types.INTEGER)
    document.volumeChange match
      case Some(v) => ps.setBigDecimal(9, v.bigDecimal)
      case None => ps.setNull(9, java.sql.Types.NUMERIC)
    ps.setString(10, document.status.dbRepr)
    ps.setString(11, document.idCheck.orNull)
    ps.setArray(12, conn.createArrayOf("text", document.playlists.toArray))
    ps.setString(13, document.rawTitle)
    ps.setString(14, document.rawDescription)
    ps.setTimestamp(15, Timestamp.from(document.createdAt.toInstant))
    ps.setTimestamp(16, Timestamp.from(document.updatedAt.toInstant))
    ps.executeUpdate()

  override def store(url: String): Task[Track] =
    val track = Track(url)
    save(track) *> getById(track._id)

  private def updateField(id: UUID, sql: String, setter: PreparedStatement => Unit): Task[Track] =
    withConnection: conn =>
      logQuery(sql, Seq(s"... id=$id"))
      val ps = conn.prepareStatement(sql)
      setter(ps)
      ps.setTimestamp(ps.getParameterMetaData.getParameterCount - 1, Timestamp.from(ZonedDateTime.now().toInstant))
      ps.setObject(ps.getParameterMetaData.getParameterCount, id)
      val updated = ps.executeUpdate()
      if updated == 0 then throw TrackNotFound(s"by id $id")
    .flatMap(_ => getById(id))

  private def updateSimpleString(id: UUID, column: String, value: String): Task[Track] =
    updateField(id, s"UPDATE tracks SET $column = ?, updated_at = ? WHERE id = ?", _.setString(1, value))

  private def updateOptionalString(id: UUID, column: String, value: Option[String]): Task[Track] =
    updateField(id, s"UPDATE tracks SET $column = ?, updated_at = ? WHERE id = ?", _.setString(1, value.orNull))

  override def updateArtist(id: UUID, artist: String): Task[Track] =
    for
      existing <- getById(id)
      idCheck = TrackId(artist, existing.title)
      _ <- withConnection: conn =>
        val sql = "UPDATE tracks SET artist = ?, id_check = ?, updated_at = ? WHERE id = ?"
        logQuery(sql, Seq(artist, idCheck, id))
        val ps = conn.prepareStatement(sql)
        ps.setString(1, artist)
        ps.setString(2, idCheck.orNull)
        ps.setTimestamp(3, Timestamp.from(ZonedDateTime.now().toInstant))
        ps.setObject(4, id)
        ps.executeUpdate()
      result <- getById(id)
    yield result

  override def updateTitle(id: UUID, title: String): Task[Track] =
    for
      existing <- getById(id)
      idCheck = TrackId(existing.artist, title)
      _ <- withConnection: conn =>
        val sql = "UPDATE tracks SET title = ?, id_check = ?, updated_at = ? WHERE id = ?"
        logQuery(sql, Seq(title, idCheck, id))
        val ps = conn.prepareStatement(sql)
        ps.setString(1, title)
        ps.setString(2, idCheck.orNull)
        ps.setTimestamp(3, Timestamp.from(ZonedDateTime.now().toInstant))
        ps.setObject(4, id)
        ps.executeUpdate()
      result <- getById(id)
    yield result

  override def updateAlbum(id: UUID, album: String): Task[Track] =
    updateSimpleString(id, "album", album)

  override def updateStartAt(id: UUID, data: Option[String]): Task[Track] =
    updateOptionalString(id, "start_at", data)

  override def updateEndAt(id: UUID, data: Option[String]): Task[Track] =
    updateOptionalString(id, "end_at", data)

  override def updateUrl(id: UUID, url: String): Task[Track] =
    withConnection: conn =>
      val sql = "UPDATE tracks SET url = ?, status = ?, updated_at = ? WHERE id = ?"
      logQuery(sql, Seq(url, TrackStatus.Draft.dbRepr, id))
      val ps = conn.prepareStatement(sql)
      ps.setString(1, url)
      ps.setString(2, TrackStatus.Draft.dbRepr)
      ps.setTimestamp(3, Timestamp.from(ZonedDateTime.now().toInstant))
      ps.setObject(4, id)
      ps.executeUpdate()
    .flatMap(_ => getById(id))

  override def updateStatus(id: UUID, status: TrackStatus): Task[Track] =
    updateSimpleString(id, "status", status.dbRepr)

  override def updateFadeOutSeconds(id: UUID, newData: Option[Int]): Task[Track] =
    updateField(id, "UPDATE tracks SET fade_out_seconds = ?, updated_at = ? WHERE id = ?", ps =>
      newData match
        case Some(v) => ps.setInt(1, v)
        case None => ps.setNull(1, java.sql.Types.INTEGER)
    )

  override def updateVolumeChange(id: UUID, newData: Option[BigDecimal]): Task[Track] =
    updateField(id, "UPDATE tracks SET volume_change = ?, updated_at = ? WHERE id = ?", ps =>
      newData match
        case Some(v) => ps.setBigDecimal(1, v.bigDecimal)
        case None => ps.setNull(1, java.sql.Types.NUMERIC)
    )

  override def updatePlaylists(id: UUID, newData: Set[String]): Task[Track] =
    withConnection: conn =>
      val sql = "UPDATE tracks SET playlists = ?, updated_at = ? WHERE id = ?"
      logQuery(sql, Seq(newData, id))
      val ps = conn.prepareStatement(sql)
      ps.setArray(1, conn.createArrayOf("text", newData.toArray))
      ps.setTimestamp(2, Timestamp.from(ZonedDateTime.now().toInstant))
      ps.setObject(3, id)
      ps.executeUpdate()
    .flatMap(_ => getById(id))

  override def updateRawTitle(id: UUID, newData: String): Task[Track] =
    updateSimpleString(id, "raw_title", newData)

  override def updateRawDescription(id: UUID, newData: String): Task[Track] =
    updateSimpleString(id, "raw_description", newData)

  override def findAllPlaylists(): Task[Map[String, Vector[Track]]] = withConnection: conn =>
    val sql = "SELECT * FROM tracks WHERE array_length(playlists, 1) > 0"
    logQuery(sql)
    val ps = conn.prepareStatement(sql)
    val rs = ps.executeQuery()
    var tracks = Vector.empty[Track]
    while rs.next() do tracks = tracks :+ readTrack(rs)
    tracks.flatMap(t => t.playlists.map(_ -> t)).groupBy(_._1).view.mapValues(_.map(_._2)).toMap

  override def search(artist: Option[String], title: Option[String], statuses: Option[Set[TrackStatus]], limit: Integer): Task[Vector[Track]] =
    withConnection: conn =>
      val conditions = Vector.newBuilder[String]
      val params = Vector.newBuilder[String]

      artist.foreach: a =>
        conditions += "artist ILIKE ?"
        params += s"%$a%"
      title.foreach: t =>
        conditions += "title ILIKE ?"
        params += s"%$t%"
      statuses.foreach: ss =>
        ss.foreach: s =>
          conditions += "status = ?"
          params += s.dbRepr

      val where = conditions.result() match
        case v if v.isEmpty => ""
        case v => " WHERE " + v.mkString(" AND ")

      val limitClause = Option(limit).map(l => s" LIMIT $l").getOrElse("")
      val sql = s"SELECT * FROM tracks$where ORDER BY updated_at DESC$limitClause"
      val paramValues = params.result()
      logQuery(sql, paramValues)

      val ps = conn.prepareStatement(sql)
      paramValues.zipWithIndex.foreach: (v, i) =>
        ps.setString(i + 1, v)

      val rs = ps.executeQuery()
      var tracks = Vector.empty[Track]
      while rs.next() do tracks = tracks :+ readTrack(rs)
      tracks

  override def findAllReadyToFetch: Task[Vector[Track]] = withConnection: conn =>
    val sql = "SELECT * FROM tracks WHERE status = 'final' AND artist != '' AND title != '' ORDER BY updated_at DESC"
    logQuery(sql)
    val ps = conn.prepareStatement(sql)
    val rs = ps.executeQuery()
    var tracks = Vector.empty[Track]
    while rs.next() do tracks = tracks :+ readTrack(rs)
    tracks

  override def genericSearch(text: String, page: Int): Task[Vector[Track]] = withConnection: conn =>
    val offset = (page - 1) * 10
    val (sql, paramValues) =
      if text.trim.isEmpty then
        ("SELECT * FROM tracks ORDER BY updated_at DESC LIMIT 10 OFFSET ?", Vector.empty[String])
      else
        val pattern = s"%$text%"
        val q = """SELECT * FROM tracks
          |WHERE url ILIKE ? OR artist ILIKE ? OR title ILIKE ? OR album ILIKE ?
          |  OR status ILIKE ? OR array_to_string(playlists, ',') ILIKE ?
          |ORDER BY updated_at DESC LIMIT 10 OFFSET ?""".stripMargin
        (q, Vector(pattern, pattern, pattern, pattern, pattern, pattern))

    logQuery(sql, paramValues :+ s"offset=$offset")

    val ps = conn.prepareStatement(sql)
    if text.trim.isEmpty then
      ps.setInt(1, offset)
    else
      paramValues.zipWithIndex.foreach: (v, i) =>
        ps.setString(i + 1, v)
      ps.setInt(paramValues.size + 1, offset)

    val rs = ps.executeQuery()
    var tracks = Vector.empty[Track]
    while rs.next() do tracks = tracks :+ readTrack(rs)
    logger.debug(s"genericSearch('$text', page=$page) returned ${tracks.size} results")
    tracks

object TracksStoragePostgres:
  val layer: ZLayer[DataSource, Nothing, ZioTracksStorageService] =
    ZLayer.fromFunction(TracksStoragePostgres(_))
