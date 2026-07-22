# Services Registry & Backup Strategy

This document maps all integrated APIs to their primary, secondary, and offline fallback providers.

---

## 1. AI, Machine Learning & NLP
| Category | Primary Provider | Secondary Provider | Offline Fallback |
| :--- | :--- | :--- | :--- |
| **LLM / Reasoning** | Google Gemini | Groq API (Llama 3) | Pre-scripted / LiteRT |
| **Speech-to-Text** | Google Cloud STT | AssemblyAI | ML Kit (On-Device) |
| **Text-to-Speech** | Cloud TTS | Play.ht | `flutter_tts` (Local) |
| **Translation** | **DeepL API** | Google Translate | Local Dictionary |

## 2. Infrastructure & Data (Firebase Bedrock)
| Category | Primary Provider | Secondary Provider | Offline Fallback |
| :--- | :--- | :--- | :--- |
| **Auth & Sync** | **Firebase Auth** | Supabase Auth | Device UUID (Local) |
| **- Google Auth** | Google SDK | Credential Manager | - |
| **- Facebook Auth** | Facebook Device Flow | Standard OAuth | - |
| **- Apple Auth** | Apple ID (Android Web) | - | - |
| **Database** | **Firestore** | Supabase (Postgres) | Hive / SQLite |
| **Analytics** | **Firebase Analytics** | PostHog | Local Log Buffer |
| **Storage** | **Firebase Storage** | Cloudinary | Local Asset Bundle |

## 3. Engagement & Monetization (AdMob & RC)
| Category | Primary Provider | Secondary Provider | Strategy |
| :--- | :--- | :--- | :--- |
| **Ads** | **AdMob** | Unity Ads | Postpone on fail |
| **Remote Config** | **Firebase RC** | Appflowy | Last Known Good (LKG) |
| **Push Alerts** | **FCM** | OneSignal | Local Notifications |

---

## 4. Master API Inventory (Reference)

### 4.1 AI, Machine Learning & NLP
* **Anthropic Claude API** (https://www.anthropic.com/api) — State-of-the-art LLMs for reasoning and conversation.
* **AssemblyAI** (https://www.assemblyai.com/) — Speech-to-text, audio intelligence, and speaker diarization APIs.
* **Baidu Translate API** (http://api.fanyi.baidu.com/) — Multi-language translation services.
* **Cohere API** (https://cohere.com/) — Enterprise NLP models specialized for text generation, embeddings, and reranking.
* **DeepAI** (https://deepai.org/) — Collection of AI models for image generation, coloring, and text analysis.
* **Deepgram** (https://deepgram.com/) — High-speed speech-to-text and language understanding API.
* **DeepL API** (https://www.deepl.com/pro-api) — High-accuracy machine translation API (500k free chars/month).
* **Eden AI** (https://www.edenai.co/) — Unified API for multiple AI technologies (translation, OCR, text-to-speech).
* **ElevenLabs** (https://elevenlabs.io/) — Generative voice AI and text-to-speech with realistic voices.
* **Google Gemini API** (https://ai.google.dev/) — Multimodal AI (text, images, video, code) with generous free usage via Google AI Studio.
* **Groq API** (https://groq.com/) — Extremely fast LPU-powered AI inference supporting Llama, Mixtral, and Gemma models.
* **Hugging Face Inference API** (https://huggingface.co/inference-api) — Access thousands of open-source ML models for NLP, vision, and audio.
* **Mistral AI API** (https://mistral.ai/) — Open-weights LLMs with high performance and competitive pricing.
* **MyMemory Translation API** (https://mymemory.translated.net/doc/spec.php) — The world's largest translation memory API.
* **NLP Cloud** (https://nlpcloud.com/) — High-performance NLP API for entity extraction, sentiment analysis, and summarization.
* **One AI** (https://www.oneai.com/) — Language AI APIs for summarization, transcription, and emotion detection.
* **OpenAI API** (https://platform.openai.com/) — Benchmark LLMs and DALL-E image generation (free starter credits on signup).
* **Perplexity API** (https://docs.perplexity.ai/) — LLM queries with real-time web search grounding.
* **Replicate** (https://replicate.com/) — Run open-source AI models in the cloud via standard HTTP requests.
* **SiliconFlow** (https://www.siliconflow.com/) — Unified cloud API platform to run, fine-tune, and deploy open-source models.
* **Stability AI API** (https://stability.ai/) — Image generation (Stable Diffusion) and editing APIs.
* **Symbl.ai** (https://symbl.ai/) — Conversation intelligence API for speech, text, and video.
* **TextRazor** (https://www.textrazor.com/) — Natural language processing for entity extraction, classification, and taxonomy.
* **VoiceRSS** (http://www.voicerss.org/) — Simple text-to-speech API supporting multiple languages.
* **Wit.ai** (https://wit.ai/) — Meta's free natural language processing platform for voice and chatbot interactions.
* **Yandex Translate** (https://translate.yandex.com/developers) — Free translation API for developers.

### 4.2 Cloud, BaaS & Developer Platforms
* **Appwrite** (https://appwrite.io/) — Open-source backend-as-a-service for Web, Mobile, and Flutter developers.
* **Auth0** (https://auth0.com/) — Identity management, SSO, and user authentication platform.
* **Back4App** (https://www.back4app.com/) — Low-code backend platform based on Parse Server.
* **Clerk** (https://clerk.com/) — Complete user management and authentication API with prebuilt UI components.
* **Cloudflare Workers / API** (https://developers.cloudflare.com/) — Deploy serverless edge functions and manage DNS/CDN settings.
* **CockroachDB Serverless** (https://www.cockroachlabs.com/) — Serverless SQL database with global scaling.
* **Cyclic** (https://www.cyclic.sh/) — Serverless app hosting with DB and storage included.
* **Deta Space** (https://deta.space/) — Personal cloud computer to host web apps and microservices.
* **Directus** (https://directus.io/) — Open-source data platform that wraps SQL databases with an instant API.
* **Firebase** (https://firebase.google.com/) — Google's suite for Firestore, Authentication, Cloud Messaging, and Hosting.
* **Fly.io API** (https://fly.io/docs/reference/api/) — Run app servers close to your users on physical servers.
* **GitHub REST / GraphQL API** (https://docs.github.com/en/rest) — Query repository data, commit histories, pull requests, and profiles.
* **Hasura Cloud** (https://hasura.io/) — Instant GraphQL API on top of your databases.
* **Koyeb** (https://www.koyeb.com/) — Developer-friendly serverless platform to deploy global apps.
* **Neon** (https://neon.tech/) — Serverless Postgres with branching and instant scaling.
* **Nhost** (https://nhost.io/) — Serverless backend platform based on Postgres, GraphQL, and Hasura.
* **PlanetScale** (https://planetscale.com/) — Serverless MySQL-compatible database platform built on Vitess.
* **PocketBase** (https://pocketbase.io/) — Open-source Go backend in a single file with SQLite and real-time subscriptions.
* **Railway API** (https://docs.railway.app/) — Cloud infrastructure platform to deploy code and databases easily.
* **Render API** (https://render.com/docs/api) — Cloud platform for hosting static sites, web apps, databases, and cron jobs.
* **Sanity.io** (https://www.sanity.io/) — Platform for structured content with real-time query API.
* **Strapi** (https://strapi.io/) — Open-source headless CMS with customizable REST/GraphQL API.
* **Supabase** (https://supabase.com/) — Open-source Firebase alternative based on PostgreSQL with realtime subscriptions and Auth.
* **SurrealDB** (https://surrealdb.com/) — Multi-model cloud database with SQL, Graph, and document capabilities.
* **Upstash** (https://upstash.com/) — Serverless Redis, Kafka, and Vector databases with HTTP REST APIs.
* **Vercel API** (https://vercel.com/docs/rest-api) — Programmatically deploy, manage domains, and control cloud infrastructure.

### 4.3 Maps, Location & Geolocation
* **Abstract IP Geolocation API** (https://www.abstractapi.com/api/ip-geolocation-api) — Look up user location and timezone from IP address.
* **BigDataCloud** (https://www.bigdatacloud.com/) — IP geolocation, reverse geocoding, and network performance APIs.
* **Country State City API** (https://countrystatecity.in/) — World countries, states, and cities database API.
* **FreeGeoIP** (https://freegeoip.app/) — Geolocation API for IP addresses.
* **Geoapify** (https://www.geoapify.com/) — Geocoding, place search, routing, and map tile REST services.
* **Geonames** (https://www.geonames.org/export/web-services.html) — Database of geographical names with coordinates and population.
* **GeoNames API** (https://www.geonames.org/) — Geographical database covering all countries and over 11 million placenames.
* **Google Maps Platform** (https://developers.google.com/maps) — Industry standard map rendering, places, and routing ($200 free monthly credit).
* **Here Maps API** (https://developer.here.com/) — Maps, routing, geocoding, and traffic services.
* **IP-API** (https://ip-api.com/) — Free geolocation API for non-commercial use.
* **ipapi** (https://ipapi.co/) — Quick REST IP lookup API returning location, currency, and timezone details.
* **IPgeolocation** (https://ipgeolocation.io/) — Geolocation, user agent, and timezone lookup by IP.
* **IPinfo** (https://ipinfo.io/) — IP geolocation API supplying ISP, city, country, and ASN details.
* **IPRegistry** (https://ipregistry.co/) — Fast IP address geolocation and threat detection API.
* **Ipstack** (https://ipstack.com/) — Locate website visitors by IP address.
* **LocationIQ** (https://locationiq.com/) — Affordable geocoding, reverse geocoding, and map tiles.
* **Mapbox** (https://www.mapbox.com/) — Custom maps, directions, vector tiles, and spatial analysis endpoints.
* **MapQuest API** (https://developer.mapquest.com/) — Directions, geocoding, and map rendering.
* **OpenCage Geocoder** (https://opencagedata.com/) — Reverse and forward geocoding API aggregating multiple databases.
* **OpenStreetMap / Nominatim** (https://nominatim.org/) — Free open geocoding and reverse geocoding data without strict limits.
* **Positionstack** (https://positionstack.com/) — Forward and reverse geocoding REST API with global coverage.
* **Radar** (https://radar.com/) — Geofencing, location tracking, and geocoding platform for mobile apps.
* **Rest Countries** (https://restcountries.com/) — Free REST endpoint providing metadata, flags, and currencies for all world countries.
* **TomTom Developer Portal** (https://developer.tomtom.com/) — Routing, traffic, maps, and search APIs.
* **ZipcodeBase** (https://zipcodebase.com/) — Postal code lookup and distance calculation API.

### 4.4 Media, Images & Design Tools
* **APITemplate.io** (https://apitemplate.io/) — Auto-generate images and PDFs from templates.
* **Bannerbear** (https://www.bannerbear.com/) — Automated image and PDF generation.
* **Cloudinary** (https://cloudinary.com/) — Image and video optimization, transformation, and asset management API.
* **DummyImage / Placeholder.com** (https://placeholder.com/) — Dynamic URL-based placeholder image generator service.
* **Filestack** (https://www.filestack.com/) — File uploading and transformation API.
* **Flaticon** (https://www.flaticon.com/) — Database of free customizable vector icons.
* **Fontsource** (https://fontsource.org/) — Open-source self-hostable Google Fonts.
* **Giphy** (https://giphy.com/) — GIF and sticker library with search and upload APIs.
* **Giphy API** (https://developers.giphy.com/) — Search, display, and share animated GIF clips and stickers.
* **Gravatar API** (https://en.gravatar.com/site/implement/) — Retrieve globally recognized user avatar images via hashed emails.
* **Iconfinder** (https://www.iconfinder.com/) — Vector icons search and download.
* **Iconify API** (https://iconify.design/docs/api/) — Access over 100,000 vector icons on-demand.
* **ImageKit** (https://imagekit.io/) — Real-time image optimization, resizing, and CDN.
* **Imgbb** (https://imgbb.com/) — Free image hosting with upload API.
* **Imgur API** (https://api.imgur.com/) — Upload, fetch, and structure web-hosted image galleries.
* **Pexels** (https://www.pexels.com/) — Curated free stock photos and videos.
* **Pexels API** (https://www.pexels.com/api/) — Free stock photo and video retrieval API.
* **Pixabay** (https://pixabay.com/) — Large collection of free photos, vectors, illustrations, and videos.
* **Pixabay API** (https://pixabay.com/api/docs/) — Search over 2.7M free photos, vectors, illustrations, and videos.
* **QR Code Generator API** (https://goqr.me/api/) — Generate custom QR code image assets dynamically via URL parameters.
* **QR Server** (https://goqr.me/) — Simple QR code generator and reader.
* **ScreenshotAPI** (https://screenshotapi.net/) — High-quality website screenshot capture.
* **Unsplash** (https://unsplash.com/) — Thousands of high-quality, royalty-free stock photos.
* **Unsplash API** (https://unsplash.com/developers) — Access millions of high-resolution royalty-free stock photos.

### 4.5 Communications, Email & Messaging
* **Ably** (https://ably.com/) — Real-time pub/sub messaging and state sync.
* **Brevo (formerly Sendinblue)** (https://www.brevo.com/) — Transactional email delivery (300 free emails/day).
* **Courier** (https://www.courier.com/) — Notification infrastructure for developer teams.
* **Discord Webhooks / API** (https://discord.com/developers/docs/intro) — Send alerts, build bots, and automate community actions.
* **Infobip** (https://www.infobip.com/) — Omnichannel communication platform.
* **Mailchimp API** (https://mailchimp.com/developer/) — Manage newsletter subscribers, campaign workflows, and audience lists.
* **Mailgun** (https://www.mailgun.com/) — Email delivery, tracking, and parsing APIs.
* **MessageBird** (https://www.messagebird.com/) — SMS, voice, and WhatsApp messaging for global businesses.
* **Novu** (https://novu.co/) — Open-source notification management system.
* **OneSignal** (https://onesignal.com/) — Push notification, email, SMS, and in-app message provider.
* **Plivo** (https://www.plivo.com/) — SMS and voice communications platform.
* **Postmark** (https://postmarkapp.com/) — Ultra-fast transactional email API service (100 free emails/month).
* **Pusher** (https://pusher.com/) — Hosted WebSockets for real-time pub/sub messaging and notifications.
* **Resend** (https://resend.com/) — Developer-first email API for transactional emails using modern stacks.
* **Sendblue** (https://sendblue.co/) — API for iMessage and SMS.
* **SendGrid** (https://sendgrid.com/) — Reliable transactional email delivery, templates, and analytics.
* **Sinch** (https://www.sinch.com/) — Messaging, voice, video, and verification APIs.
* **Slack API** (https://api.slack.com/) — Custom bot integrations, interactive messages, and workspace automation.
* **Socket.io** (https://socket.io/) — Bidirectional, low-latency, event-based communication.
* **Telegram Bot API** (https://core.telegram.org/bots/api) — Complete control API to build bots, integrations, and automated notifications.
* **Textlocal** (https://www.textlocal.com/) — Bulk SMS services and developer APIs.
* **Twilio** (https://www.twilio.com/) — Programmable SMS, WhatsApp messaging, and Voice call capabilities.
* **Twilio SendGrid** (https://sendgrid.com/) — Email infrastructure, delivery, and template management.
* **Vonage (Nexmo)** (https://www.vonage.com/) — APIs for SMS, voice, video, and verification.

### 4.6 Financial, Currency & E-Commerce
* **Adyen** (https://www.adyen.com/) — End-to-end payments platform.
* **Alpha Vantage** (https://www.alphavantage.co/) — Real-time and historical stock, forex, and cryptocurrency market data.
* **Coinbase API** (https://developers.coinbase.com/) — Integrate bitcoin, ethereum, and other crypto wallet features.
* **CoinCap API** (https://coincap.io/) — Real-time market metrics for hundreds of cryptocurrencies.
* **CoinGecko** (https://www.coingecko.com/) — Cryptocurrency market capitalization and price charts.
* **CoinGecko API** (https://www.coingecko.com/en/api) — Cryptocurrency prices, volume, market caps, and historical tracking.
* **CryptoCompare** (https://www.cryptocompare.com/) — Real-time crypto market data and news.
* **ExchangeRate-API** (https://www.exchangerate-api.com/) — Accurate exchange rates and currency conversion calculations.
* **Finicity** (https://www.finicity.com/) — Open banking platform and credit decisioning APIs.
* **Finnhub** (https://finnhub.io/) — Real-time stock market quotes, financial fundamentals, and market news.
* **Frankfurter** (https://www.frankfurter.app/) — Currency exchange rates from the European Central Bank.
* **Frankfurter API** (https://www.frankfurter.app/) — Open-source currency exchange rate API tracking European Central Bank data.
* **Messari** (https://messari.io/) — Crypto asset market intelligence and analytics.
* **Open Exchange Rates** (https://openexchangerates.org/) — Standard forex exchange rate data service.
* **PayPal Developer** (https://developer.paypal.com/) — Global payment integrations.
* **Plaid** (https://plaid.com/) — Secure connection API linking bank accounts to financial apps (free Sandbox tier).
* **Stripe** (https://stripe.com/) — Online payment processing for businesses.
* **Stripe API** (https://stripe.com/docs/api) — Payment processing, subscription handling, and invoicing primitives.
* **TaxJar API** (https://www.taxjar.com/) — Real-time sales tax calculation by location.
* **Wise (TransferWise)** (https://wise.com/us/business/api) — Cross-border payments and exchange rate API.
* **Yodlee** (https://www.yodlee.com/) — Financial data aggregation and analytics API.

### 4.7 Weather, Environment & Science
* **AerisWeather** (https://www.aerisweather.com/) — Premium weather maps, forecasts, and storm tracking.
* **AirVisual** (https://www.iqair.com/) — Global air quality index and pollution tracking.
* **AirVisual API** (https://www.iqair.com/air-pollution-data-api) — Global air quality index (AQI) and atmospheric data monitoring.
* **AQICN** (https://aqicn.org/api/) — Worldwide Air Quality Index API.
* **CO2 Signal / Electricity Maps** (https://www.electricitymaps.com/) — Real-time carbon intensity and clean energy grid metrics.
* **eBird API** (https://ebird.org/home) — Global bird sighting observations and biodiversity species location data.
* **Electricity Maps** (https://www.electricitymaps.com/) — Real-time carbon footprint of electricity grids.
* **Launch Library 2** (https://thespacedevs.com/llapi) — Comprehensive information on spaceflight rocket launches and missions.
* **Met Office API** (https://www.metoffice.gov.uk/services/data) — UK weather forecast data.
* **Meteostat** (https://meteostat.net/) — Open historical weather and climate data.
* **NASA Open APIs** (https://api.nasa.gov/) — Access astronomy picture of the day (APOD), satellite imagery, and asteroid tracking data.
* **National Weather Service (NOAA)** (https://www.weather.gov/documentation/services-web-api) — Free US weather forecasts and warnings.
* **Open-Meteo** (https://open-meteo.com/) — Free open-source weather API providing forecast models without requiring an API key.
* **OpenWeather** (https://openweathermap.org/) — Weather forecasts, historical data, and maps.
* **OpenWeatherMap** (https://openweathermap.org/api) — Current weather conditions, 5-day forecasts, and air pollution indexes.
* **Storm Glass** (https://stormglass.io/) — Global marine weather forecasts (waves, wind, currents).
* **Sunrise-Sunset** (https://sunrise-sunset.org/) — Sunrise and sunset times for any coordinate.
* **Sunrise-Sunset API** (https://sunrise-sunset.org/api) — Calculate precise sunrise and sunset times for given geographical coordinates.
* **Tomorrow.io** (https://www.tomorrow.io/) — Real-time and historical weather forecasts, air quality, and alerts.
* **USGS Earthquake API** (https://earthquake.usgs.gov/fdsnws/event/1/) — Real-time seismic event tracking and historical earthquake logs.
* **Visual Crossing** (https://www.visualcrossing.com/) — Global historical weather data and forecasts.
* **WeatherAPI** (https://www.weatherapi.com/) — Weather forecasts, air quality, sports events, and historical weather lookup.
* **Weatherbit** (https://www.weatherbit.io/) — High-performance weather forecast and historical APIs.

### 4.8 Entertainment, Movies & Content
* **Anilist** (https://graphql.anilist.co/) — GraphQL API for anime and manga tracking.
* **Comic Vine API** (https://comicvine.gamespot.com/api/) — Metadata on comic books, characters, creators, and publishers.
* **Deezer API** (https://developers.deezer.com/) — Music streaming catalog and user data.
* **Edamam** (https://developer.edamam.com/) — Recipe search, nutrition analysis, and food database.
* **Genius API** (https://genius.com/developers) — Lyrics, music metadata, and artist info.
* **Giant Bomb** (https://www.giantbomb.com/api/) — Video game wiki database.
* **Jikan API** (https://jikan.moe/) — Unofficial MyAnimeList REST API providing anime and manga metadata.
* **Jikan MyAnimeList API** (https://jikan.moe/) — Anime and manga search and metadata.
* **Kitsu** (https://kitsu.io/) — Modern anime, manga, and social network API.
* **Last.fm** (https://www.last.fm/api) — Music scrobbling and recommendation database.
* **Musixmatch** (https://developer.musixmatch.com/) — World's largest lyrics catalog.
* **OMDb** (https://www.omdbapi.com/) — Movie info and ratings (including IMDb).
* **OMDb API** (https://www.omdbapi.com/) — Open movie database REST service supporting IMDb ratings and movie info.
* **Open Library API** (https://openlibrary.org/developers/api) — Internet Archive project indexing millions of book records and covers.
* **PokeAPI** (https://pokeapi.co/) — Highly detailed RESTful API containing full Pokémon franchise statistics.
* **RAWG Video Games API** (https://rawg.io/apidocs) — Massive database covering over 500,000 video games across multiple platforms.
* **RAWG Video Games Database** (https://rawg.io/apidocs) — Huge video games metadata database.
* **Spoonacular** (https://spoonacular.com/food-api) — Food recipes, ingredients, and nutrition search.
* **Spoonacular Food API** (https://spoonacular.com/food-api) — Recipe search, ingredient parsing, and nutritional breakdown analysis.
* **Spotify API** (https://developer.spotify.com/) — Music metadata, search, and user playback control.
* **Spotify Web API** (https://developer.spotify.com/) — Query music metadata, artist profiles, playlists, and playback state.
* **TMDB** (https://www.themoviedb.org/) — Movie and TV show metadata, ratings, and posters.
* **TMDB (The Movie Database)** (https://developer.themoviedb.org/) — Extensive database of movies, TV shows, actors, ratings, and posters.
* **TVmaze** (https://www.tvmaze.com/api) — TV show schedule and episode guide.
* **YouTube Data API** (https://developers.google.com/youtube/v3) — Search videos, manage playlists, and analyze channel metrics.

### 4.9 News, Search & Reference Data
* **Bing Search API** (https://www.microsoft.com/en-us/bing/apis) — Web search, news, images, and videos.
* **Contextual Web Search** (https://rapidapi.com/contextualwebsearch/api/web-search/) — Safe search, news, and images API.
* **Crossref REST API** (https://www.crossref.org/documentation/retrieve-metadata/rest-api/) — Search metadata for millions of peer-reviewed academic papers.
* **Currents API** (https://currentsapi.services/) — Multi-language global news collection API.
* **Dictionary API** (https://dictionaryapi.dev/) — Free english dictionary definitions, phonetics, and audio pronunciations.
* **DuckDuckGo Instant Answer API** (https://duckduckgo.com/api) — Zero-click info boxes, topic disambiguation, and quick definitions.
* **DuckDuckGo Instant Answers** (https://duckduckgo.com/api) — Quick answers, topics, and definitions.
* **GNews** (https://gnews.io/) — Search Google News headlines and content.
* **Gutendex** (https://gutendex.com/) — Web API for Project Gutenberg's library of public-domain books.
* **Hacker News API** (https://github.com/HackerNews/API) — Real-time stories, comments, and job posts.
* **Hacker News Firebase API** (https://github.com/HackerNews/API) — Real-time access to top stories, comments, and user profiles.
* **MediaBiasFactCheck** (https://mediabiasfactcheck.com/) — Check media source credibility and bias.
* **New York Times API** (https://developer.nytimes.com/) — Articles, reviews, and best-seller lists.
* **NewsAPI** (https://newsapi.org/) — Live headlines and historical article indexing from thousands of international news publications.
* **Open Trivia DB** (https://opentdb.com/) — Category-driven trivia question database for game creation.
* **OpenSanctions** (https://www.opensanctions.org/) — Search lists of politically exposed persons (PEPs) and sanctions.
* **Tavily** (https://tavily.com/) — Search API optimized for LLMs and AI agents.
* **Tavily Search API** (https://tavily.com/) — AI-optimized web search query API for LLM retrieval and agent search.
* **The Guardian API** (https://open-platform.theguardian.com/) — Full text of articles from The Guardian.
* **WebHose** (https://webhose.io/) — Structured data feeds from news, blogs, and discussions.
* **Wikipedia API** (https://www.mediawiki.org/wiki/API:Main_page) — Fetch article excerpts, structured media, and full wiki page data.

### 4.10 Prototyping, Mocking & Utility
* **Beeceptor** (https://beeceptor.com/) — Instant interceptor and mock server creator.
* **DummyJSON** (https://dummyjson.com/) — Free fake REST API providing mock ecommerce, user, and post datasets.
* **Faker API** (https://fakerapi.it/) — Generate mock structured JSON payload items (addresses, products, companies).
* **Have I Been Pwned API** (https://haveibeenpwned.com/API/v3) — Check if account credentials have appeared in public data breaches.
* **Httpbin** (https://httpbin.org/) — Simple HTTP request and response testing.
* **httpbin.org** (https://httpbin.org/) — HTTP request and response testing service returning exact headers and methods sent.
* **IFTTT** (https://ifttt.com/) — Automate tasks between apps and devices.
* **JSONPlaceholder** (https://jsonplaceholder.typicode.com/) — Fake REST API for rapid frontend prototyping and CRUD operation testing.
* **Make (Integromat)** (https://www.make.com/) — Visually build and automate workflows.
* **Mockaroo** (https://www.mockaroo.com/) — Create custom mock CSV, JSON, and SQL datasets.
* **Mockoon** (https://mockoon.com/) — Desktop app and CLI to mock local APIs.
* **Pipedream** (https://pipedream.com/) — Connect APIs and run workflows with low code.
* **Postman Echo** (https://postman-echo.com/) — Echo back headers, query parameters, and body payloads.
* **ReqRes** (https://reqres.in/) — Simulated REST API with pre-baked responses for testing client requests.
* **Restful Booker** (https://restful-booker.herokuapp.com/) — Intentional hotel booking API crafted specifically for API automation testing.
* **Shields.io** (https://shields.io/) — Concise dynamic SVG status badge generation API for GitHub README files.
* **Swagger Petstore** (https://petstore.swagger.io/) — Benchmark OpenAPI spec sample API for testing REST clients.
* **URLHaus API** (https://urlhaus-api.abuse.ch/) — Malware URL database feed for threat intelligence testing.
* **WireMock** (https://wiremock.org/) — API mocking and testing framework.
* **Zapier** (https://zapier.com/) — Automated workflows between web applications.

### 4.11 Security, Identity & Compliance
* **AbuseIPDB** — Check and report IP addresses engaging in abusive behavior or spam.
* **AlienVault OTX** (https://otx.alienvault.com/) — Open threat exchange intelligence feeds.
* **Authress** — B2B authorization-as-a-service platform for fine-grained access control.
* **Censys** (https://censys.io/) — Attack surface management and search engine for hosts.
* **Clerk** (https://clerk.com/) — Authentication and user management service.
* **Descope** (https://www.descope.com/) — Authentication, verification, and user management flow builder.
* **Fingerprint API** — High-accuracy device identification and fraud prevention API.
* **Frontegg** (https://frontegg.com/) — Customer identity and user management platform.
* **GreyNoise** (https://www.greynoise.io/) — Analyze background noise of the internet to filter alerts.
* **Have I Been Pwned** (https://haveibeenpwned.com/) — Check credentials against known data breaches.
* **Have I Been Pwned API** — Check if passwords or account credentials have appeared in public data breaches.
* **IP Quality Score** (https://www.ipqualityscore.com/) — Proxy, VPN, and fraud detection API.
* **Passbase API** — Identity verification and KYC infrastructure APIs.
* **PhishTank** (https://www.phishtank.com/) — Collaborative clearinghouse for data on phishing.
* **Secoda API** — Data discovery, lineage, and cataloguing automation platform.
* **Shodan** (https://www.shodan.io/) — Search internet-connected devices, open ports, and vulnerabilities.
* **Shodan API** — Search engine for internet-connected devices, open ports, and vulnerabilities.
* **SSL Labs API** — Automated server test for SSL/TLS configuration analysis.
* **Stytch API** — Developer-first passwordless authentication APIs and SDKs.
* **URLScan** (https://urlscan.io/) — Analyze and capture screenshots of suspicious websites.
* **VirusTotal** (https://www.virustotal.com/) — File, URL, domain, and IP malware analysis.
* **VirusTotal API** — Analyze suspicious files, domains, IP addresses, and URLs for malware.

### 4.12 Productivity, Documents & Office Tools
* **Airtable API** — Turn spreadsheets into relational databases with full REST access.
* **Asana API** — Task tracking, project management, and workspace automation.
* **Box API** (https://developer.box.com/) — Cloud content management and storage.
* **ClickUp API** (https://clickup.com/api) — Task tracking, docs, and goals integration.
* **Cloudmersive Document Processing** — Convert, edit, and parse docx, pdf, xlsx, and image formats.
* **Discord API** (https://discord.com/developers/docs/intro) — Chat bots, servers, and channels control.
* **DocRaptor** — Enterprise HTML-to-PDF and XML document generation service.
* **DocuSign API** (https://developers.docusign.com/) — Programmatic e-signatures and document workflows.
* **Dropbox API** (https://www.dropbox.com/developers) — Manage files, folders, and sync states.
* **Google Drive API** — Manage files, folders, permissions, and cloud storage programmatically.
* **Microsoft Graph** (https://developer.microsoft.com/en-us/graph) — Mail, calendar, drive access across Office 365.
* **Microsoft Graph API** — Unified endpoint for Office 365, Outlook, OneDrive, and SharePoint data.
* **Monday.com API** (https://developer.monday.com/) — Work management platform integrations.
* **Notion API** — Programmatically read, write, and structure pages and databases in Notion.
* **PDFShift** — High-speed HTML-to-PDF generation REST API for invoices and reports.
* **Slack API** (https://api.slack.com/) — Send messages, build bots, and automate workspaces.
* **Telegram Bot API** (https://core.telegram.org/bots) — Message alerts and interactive bots.
* **Todoist API** — Manage tasks, projects, labels, and personal productivity streams.
* **Trello API** (https://developer.atlassian.com/cloud/trello/) — Manage kanban boards and project flows.
* **Trello REST API** — Manage boards, lists, cards, and team workflows.

### 4.13 Social Media, Analytics & Community
* **Behance API** — Access creative project showcases, galleries, and designer profiles.
* **Dev.to API** — Publish, fetch, and manage developer articles and community posts.
* **Dribbble API** — Showcase and discover creative design shots, portfolios, and user activity.
* **Hashnode API** — GraphQL API for blogging platforms and developer publishing workflows.
* **Instagram Graph API** (https://developers.facebook.com/docs/instagram-api/) — Search media, tags, and comments.
* **Mastodon API** — Decentralized microblogging platform REST and streaming API.
* **Medium API** — Publish and manage stories on Medium publications.
* **Pinterest API** — Pin creation, board management, and visual discovery data.
* **Reddit API** — Access subreddit threads, user profiles, comments, and voting metrics.
* **Substack API** — Newsletter subscription management and publication content retrieval.
* **TikTok API** (https://developers.tiktok.com/) — Video metadata and content sharing.
* **Twitch API** — Live streaming metadata, user profiles, clips, and chat interactions.
* **Twitter / X API** (https://developer.x.com/) — Query tweets, spaces, lists, and direct messages.
* **Vimeo API** (https://developer.vimeo.com/) — Upload, manage, and configure video players.
* **YouTube Data API** (https://developers.google.com/youtube/v3) — Search videos, playlists, and channels.

### 4.14 Audio, Music & Speech Processing
* **Acoustid** (https://acoustid.org/webservice) — Audio fingerprinting and database lookup.
* **Apple Music API** (https://developer.apple.com/documentation/applemusicapi) — Access apple music catalog and user playlists.
* **Audible API** — Audiobook catalogs, narrator details, and listening metrics.
* **Deepgram** (https://deepgram.com/) — Speech-to-text, diarization, and audio intelligence.
* **Deepgram API** — Enterprise-grade speech-to-text, audio intelligence, and voice AI platform.
* **Deezer API** (https://developers.deezer.com/) — Music streaming catalog and search.
* **ElevenLabs** (https://elevenlabs.io/) — Generative AI voice cloning and speech synthesis.
* **ElevenLabs API** — Ultra-realistic generative AI voice cloning and text-to-speech synthesis.
* **Free Music Archive** (https://freemusicarchive.org/api) — Search and download royalty-free music.
* **Freesound API** (https://freesound.org/docs/api/) — Search and download audio samples under Creative Commons.
* **Genius API** — Lyrics search, annotations, and artist song metadata.
* **Last.fm API** — Music catalog metadata, artist tracks, user scrobbles, and recommendations.
* **Play.ht** (https://play.ht/) — AI text-to-speech voice generation.
* **Play.ht API** — AI text-to-speech audio generation and podcast hosting tools.
* **Radio-Browser** (https://www.radio-browser.info/) — Open directory of streaming online radio stations.
* **Radio-Browser API** — Open directory of streaming online radio stations worldwide.
* **Rev.ai** — Speech-to-text transcription and subtitle generation APIs.
* **SoundCloud API** (https://developers.soundcloud.com/) — Upload and search audio tracks.
* **Speechmatics** (https://www.speechmatics.com/) — Advanced speech recognition and multilingual transcription.
* **Speechmatics API** — Advanced speech recognition and multilingual transcription.
* **VocalRemover API** — AI-powered vocal isolation and music stem separation.

### 4.15 Gaming, Anime & Pop Culture
* **Anilist API** -- GraphQL API for tracking anime, manga, and character stats.
* **Chess.com API** — Player stats, leaderboards, live game archives, and puzzles.
* **D&D 5e SRD API** (https://www.dnd5eapi.co/) — Spells, monsters, and rules for Dungeons & Dragons.
* **Deck of Cards API** (https://deckofcardsapi.com/) — Simulate shuffling and drawing playing cards.
* **Fortnite API** — Cosmetic items, map details, and shop rotations.
* **Giant Bomb API** — Video game encyclopedia data, characters, concepts, and reviews.
* **IGDB API** — Internet Game Database providing extensive game metadata, covers, and release dates.
* **Jikan API** (https://jikan.moe/) — Unofficial MyAnimeList REST API.
* **Kitsu API** — Modern anime and manga database with rich filtering and categorization.
* **League of Legends Riot API** — Summoner profiles, match histories, and game telemetry data.
* **Lichess API** — Open chess server API for games, analysis, puzzles, and bot integration.
* **MangaDex API** (https://api.mangadex.org/) — Manga database and reader integrations.
* **Minecraft API** — Player UUID lookups, skin textures, and game session verification.
* **PokeAPI** (https://pokeapi.co/) — Comprehensive Pokémon stats and data.
* **Riot Games API** (https://developer.riotgames.com/) — League of Legends, Valorant, and Legends of Runeterra metrics.
* **Steam Web API** — Query user inventories, game achievements, friend lists, and store data.

### 4.16 IoT, Hardware & Smart Home
* **Adafruit IO** — Simple IoT data visualization, storage, and device control broker.
* **Blynk API** — IoT firmware and mobile dashboard builder platform.
* **ESPHome** (https://esphome.io/) — Compile and control ESP8266/ESP32 custom firmware.
* **ESPHome API** — Control ESP8266/ESP32 boards using simple configuration files.
* **Home Assistant API** — Open-source home automation hub REST and WebSocket API.
* **IFTTT API** — Trigger cross-service automations between smart hardware and web apps.
* **Netatmo API** (https://dev.netatmo.com/) — Weather station, thermostat, and camera data.
* **OpenHardwareIO** — Community database for open-source electronics and schematics.
* **Particle API** — IoT hardware cloud platform for connected microcontrollers.
* **Philips Hue API** (https://developers.meethue.com/) — Control smart bulbs locally.
* **Shelly Cloud API** (https://shelly-api-docs.shelly.cloud/) — Smart relays, plugs, and sensors API.
* **SmartThings API** — Control smart home devices, sensors, and lighting systems.
* **Sonoff API** (https://sonoff.tech/) — Smart switches and plugs controls.
* **Tasmota API** (https://tasmota.github.io/docs/) — Open-source firmware for ESP8266 devices.
* **Tuya IoT API** — Cloud developer platform for smart home appliances and hardware.
* **Wappalyzer API** — Identify technologies, CMS frameworks, and hardware used on websites.

### 4.17 Travel, Transport & Navigation
* **Amadeus API** (https://developers.amadeus.com/) — Search flights, hotel rooms, and points of interest.
* **Amadeus for Developers** — Airline flight booking, hotel search, and travel safety APIs.
* **Amtrak API** — Train schedules, station data, and rail transit routes.
* **Aviationstack** — Real-time flight tracking data, airline schedules, and airport codes.
* **BGBike / City Bikes API** — Real-time public bicycle sharing systems worldwide.
* **City Bikes API** (https://api.citybik.es/v2/) — Real-time public bicycle sharing systems.
* **FlightAware API** (https://www.flightaware.com/commercial/aeroapi/) — Detailed flight tracking, alerts, and historical data.
* **Geoapify Routing** (https://www.geoapify.com/routing-api) — Route calculation for driving, walking, and cycling.
* **Geoapify Routing API** — Multi-modal route planning for driving, walking, and cycling.
* **Lyft API** (https://developer.lyft.com/) — Rideshare ETAs, pricing, and driver bookings.
* **Navitia API** (https://doc.navitia.io/) — Public transit routes and timetables.
* **OpenSky Network** (https://opensky-network.org/) — Live air traffic transponder signals.
* **OpenSky Network API** — Live air traffic control data and aircraft transponder signals.
* **Overpass API** — Custom read-only queries against OpenStreetMap spatial data.
* **Overpass OSM API** (https://wiki.openstreetmap.org/wiki/Overpass_API) — Spatial queries against OpenStreetMap database.
* **Parkopedia** (https://www.parkopedia.com/) — Global parking space prices and real-time availability.
* **Parkopedia API** — Global parking space availability, pricing, and location details.
* **Rome2Rio** (https://www.rome2rio.com/documentation/) — Transport search engine comparing flights, trains, buses, and driving.
* **Skyscanner API** — Global flight pricing, schedules, and travel aggregation data.
* **Transit Land** (https://www.transit.land/) — Open transit data for bus, rail, and ferry networks.
* **Transit Land API** — Unified open transit data for global bus, rail, and ferry networks.
* **Uber API** (https://developer.uber.com/) — Rideshare estimations, trip requests, and delivery.

### 4.18 Reference, Genealogy & Fact-Checking
* **Borenstein Country Info** (https://restcountries.com/) — World country metadata, currencies, and borders.
* **Crossref API** (https://www.crossref.org/) — Academic research papers metadata search.
* **DBpedia** (https://wiki.dbpedia.org/sparql-endpoint) — Extract structured data from Wikipedia databases.
* **DBpedia SPARQL Endpoint** -- Query structured data extracted from Wikipedia editions.
* **Every Politician** (https://everypolitician.org/) — Structured databases of politicians and parliaments.
* **Every Politician API** — Data on politicians, parliaments, and legislative bodies worldwide.
* **FactCheck.org** (https://www.factcheck.org/) — Fact-checking political claims and urban legends.
* **FactCheck.org API** — Political fact-checking claims and investigative reports.
* **FamilySearch** (https://www.familysearch.org/developers/) — Family trees and historical census archives.
* **FamilySearch API** — Global genealogical records, family trees, and historical archives.
* **Harvard Art Museums API** (https://github.com/harvardartmuseums/api-docs) — Digital archives of Harvard art collections.
* **Internet Archive API** — Access millions of archived web pages, books, audio, and video files.
* **Library of Congress** (https://www.loc.gov/apis/) — Historical photographs, maps, and transcripts.
* **Library of Congress API** — Digital collections, photographs, maps, and historical manuscripts.
* **Open Library API** (https://openlibrary.org/developers/api) — Free books catalog database.
* **OpenSanctions** (https://www.opensanctions.org/) — Search lists of politically exposed persons (PEPs) and sanctions.
* **OpenSanctions API** — Track global politically exposed persons, sanctions, and crime databases.
* **Snopes** (https://www.snopes.com/) — Misinformation database check.
* **Snopes API** — Urban legends, rumors, and misinformation verification database.
* **Wikidata API** — Structured knowledge base querying entities, facts, and relations.
* **World Bank API** (https://data.worldbank.org/) — Development indicators and economic data.

### 4.19 Environment, Energy & Sustainability
* **Carbon Interface** — Carbon footprint estimation API for electricity, flights, and shipping.
* **Climatiq API** — Carbon emission calculation engine based on authoritative scientific models.
* **Copernicus API** (https://copernicus.eu/) — Earth observation satellite data.
* **FAOSTAT API** — Food and agriculture statistics, crop yields, and livestock data.
* **Global Earthquake Model** (https://www.globalquakemodel.org/) — Seismic hazard and risk datasets.
* **Global Forest Watch** (https://www.globalforestwatch.org/) — Forest fire and deforestation data monitoring.
* **Global Forest Watch API** — Monitor deforestation, forest fires, and land cover changes.
* **NOAA Climate API** (https://www.ncdc.noaa.gov/cdo-web/) — Climate indices and historical weather station data.
* **NOAA Climate Data Online API** — Historical weather observations and climate research datasets.
* **OECD API** (https://data.oecd.org/) — Global economic and social indicators.
* **OECD Data API** — Economic statistics and social indicators across member nations.
* **OEDI API** (https://www.energy.gov/eere/about-us/open-energy-data-initiative) — Energy resources and power grid information.
* **Open Energy Data Initiative (OEDI)** — U.S. Department of Energy open power systems and renewable energy.
* **OpenAQ** (https://openaq.org/) — Real-time air quality measurements from around the world.
* **PVWatts API** (https://developer.nrel.gov/docs/solar/pvwatts/) — Estimations of solar energy output for solar PV systems.
* **UN Comtrade** (https://comtrade.un.org/) — Global trade statistics import and export database.
* **UN Comtrade API** — International trade statistics and commodity import/export records.
* **WaterAPI** — Real-time streamflow, water temperature, and hydrological metrics from USGS.
* **World Bank Open Data** (https://data.worldbank.org/) — Global development indicators and country statistics.
* **World Bank Open Data API** — Global development indicators, poverty metrics, and economic data.
