--
-- PostgreSQL database dump
--

SET statement_timeout = 0;
SET lock_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SET check_function_bodies = false;
SET client_min_messages = warning;

--
-- Name: plpgsql; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS plpgsql WITH SCHEMA pg_catalog;


--
-- Name: EXTENSION plpgsql; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION plpgsql IS 'PL/pgSQL procedural language';


--
-- Name: postgis; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS postgis WITH SCHEMA public;


--
-- Name: EXTENSION postgis; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION postgis IS 'PostGIS geometry, geography, and raster spatial types and functions';


SET search_path = public, pg_catalog;

SET default_tablespace = '';

SET default_with_oids = false;

--
-- Name: datapoints; Type: TABLE; Schema: public; Owner: -; Tablespace: 
--

CREATE TABLE datapoints (
    gid integer NOT NULL,
    geog geography(PointZ,4326),
    start_time timestamp with time zone,
    end_time timestamp with time zone,
    data jsonb,
    stream_id integer
);


--
-- Name: geoindex_gid_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE geoindex_gid_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: geoindex_gid_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE geoindex_gid_seq OWNED BY datapoints.gid;


--
-- Name: sensors; Type: TABLE; Schema: public; Owner: -; Tablespace: 
--

CREATE TABLE sensors (
    gid integer NOT NULL,
    name character varying(255),
    geog geography(PointZ,4326),
    created timestamp without time zone,
    metadata json
);


--
-- Name: sensors_gid_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE sensors_gid_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sensors_gid_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE sensors_gid_seq OWNED BY sensors.gid;


--
-- Name: streams; Type: TABLE; Schema: public; Owner: -; Tablespace: 
--

CREATE TABLE streams (
    gid integer NOT NULL,
    name character varying(255),
    geog geography(PointZ,4326),
    created timestamp without time zone,
    metadata json,
    sensor_id integer,
    start_time timestamp with time zone,
    end_time timestamp with time zone,
    params text[]
);


--
-- Name: streams_gid_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE streams_gid_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: streams_gid_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE streams_gid_seq OWNED BY streams.gid;


--
-- Name: gid; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY datapoints ALTER COLUMN gid SET DEFAULT nextval('geoindex_gid_seq'::regclass);


--
-- Name: gid; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY sensors ALTER COLUMN gid SET DEFAULT nextval('sensors_gid_seq'::regclass);


--
-- Name: gid; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY streams ALTER COLUMN gid SET DEFAULT nextval('streams_gid_seq'::regclass);


--
-- Name: geoindex_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--

ALTER TABLE ONLY datapoints
    ADD CONSTRAINT geoindex_pkey PRIMARY KEY (gid);


--
-- Name: sensors_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--

ALTER TABLE ONLY sensors
    ADD CONSTRAINT sensors_pkey PRIMARY KEY (gid);


--
-- Name: streams_pkey; Type: CONSTRAINT; Schema: public; Owner: -; Tablespace: 
--

ALTER TABLE ONLY streams
    ADD CONSTRAINT streams_pkey PRIMARY KEY (gid);


--
-- Name: datapoints_end_time_idx; Type: INDEX; Schema: public; Owner: -; Tablespace: 
--

CREATE INDEX datapoints_end_time_idx ON datapoints USING btree (end_time);


--
-- Name: datapoints_start_end_time_idx; Type: INDEX; Schema: public; Owner: -; Tablespace: 
--

CREATE INDEX datapoints_start_end_time_idx ON datapoints USING btree (start_time, end_time);


--
-- Name: datapoints_stream_id_idx; Type: INDEX; Schema: public; Owner: -; Tablespace: 
--

CREATE INDEX datapoints_stream_id_idx ON datapoints USING btree (stream_id);


--
-- Name: geoindex_gix; Type: INDEX; Schema: public; Owner: -; Tablespace: 
--

CREATE INDEX geoindex_gix ON datapoints USING gist (geog);


--
-- Name: sensors_gix; Type: INDEX; Schema: public; Owner: -; Tablespace: 
--

CREATE INDEX sensors_gix ON sensors USING gist (geog);


--
-- Name: streams_gix; Type: INDEX; Schema: public; Owner: -; Tablespace: 
--

CREATE INDEX streams_gix ON streams USING gist (geog);


--
-- Name: streams_sensor_id_idx; Type: INDEX; Schema: public; Owner: -; Tablespace: 
--

CREATE INDEX streams_sensor_id_idx ON streams USING btree (sensor_id);

CREATE INDEX geoindex_times ON datapoints (start_time, end_time);

CREATE INDEX geoindex_stream_id ON datapoints (stream_id);

CREATE INDEX datapoints_data_idx ON datapoints USING gin (data);

--
-- PostgreSQL database dump complete
--

