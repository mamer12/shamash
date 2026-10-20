# Performance on cheap TVs

Reference TV: TCL with MediaTek MT5889, 2.7 GB RAM, Android 11.

- PERF-01 Cold start to first frame of Home ≤ 1.5 s; returning Home from an app ≤ 300 ms to a fully drawn screen.
- PERF-02 Scrolling rows and moving focus: 90th percentile frame ≤ 16 ms, janky frames ≤ 5% (`dumpsys gfxinfo`).
- PERF-03 Shamash's home process ≤ 120 MB PSS after 10 minutes of browsing.
- PERF-04 Images are requested at the size they are drawn (Coil size); the carousel uses ≤ 1280×720.
- PERF-05 Data arriving from several providers at once triggers one recomposition of the rows (state is combined
  before it reaches the UI).
