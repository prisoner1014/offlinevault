package com.offlinevault.app.ui.screens.games

object GameHtmlProvider {

    private const val COMMON_HEAD = """
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover">
    <style>
        * { box-sizing: border-box; -webkit-tap-highlight-color: transparent; user-select: none; -webkit-user-select: none; margin: 0; padding: 0; }
        html, body {
            width: 100%; height: 100%;
            background-color: #090D16;
            color: #E2E8F0;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
            overflow: hidden;
            touch-action: none;
        }
        .btn {
            background: #1E293B;
            color: #38BDF8;
            border: 1px solid #334155;
            padding: 10px 16px;
            border-radius: 12px;
            font-size: 15px;
            font-weight: 600;
            cursor: pointer;
            display: inline-flex;
            align-items: center;
            justify-content: center;
            min-height: 48px;
            min-width: 48px;
            touch-action: manipulation;
            transition: transform 0.08s ease, background 0.08s ease, box-shadow 0.08s ease;
            box-shadow: 0 2px 4px rgba(0,0,0,0.3);
        }
        .btn:active, .btn.touch-active {
            background: #38BDF8 !important;
            color: #090D16 !important;
            transform: scale(0.92);
            box-shadow: 0 0 16px rgba(56, 189, 248, 0.7);
        }
        .hud {
            display: flex;
            align-items: center;
            justify-content: space-between;
            padding: 8px 16px;
            background: #0F172A;
            border-bottom: 1px solid #1E293B;
            width: 100%;
            z-index: 10;
            touch-action: manipulation;
        }
        .hud .btn {
            min-height: 38px;
            padding: 6px 14px;
            font-size: 13px;
        }
        .badge {
            background: #1E293B;
            padding: 4px 10px;
            border-radius: 6px;
            font-size: 13px;
            font-weight: bold;
            color: #38BDF8;
            border: 1px solid #334155;
        }
    </style>
    <script>
        // Web Audio Synthesizer for 100% offline retro sound effects
        const AudioFX = {
            ctx: null,
            muted: false,
            vibrate(ms = 12) {
                if (navigator.vibrate) {
                    try { navigator.vibrate(ms); } catch (e) {}
                }
            },
            init() {
                if (!this.ctx) {
                    const AC = window.AudioContext || window.webkitAudioContext;
                    if (AC) this.ctx = new AC();
                }
                if (this.ctx && this.ctx.state === 'suspended') {
                    this.ctx.resume();
                }
            },
            play(type) {
                this.vibrate(type === 'win' || type === 'over' ? 35 : 12);
                if (this.muted) return;
                try {
                    this.init();
                    if (!this.ctx) return;
                    const t = this.ctx.currentTime;
                    const osc = this.ctx.createOscillator();
                    const gain = this.ctx.createGain();
                    osc.connect(gain);
                    gain.connect(this.ctx.destination);

                    if (type === 'tap' || type === 'move') {
                        osc.frequency.setValueAtTime(440, t);
                        osc.frequency.exponentialRampToValueAtTime(660, t + 0.05);
                        gain.gain.setValueAtTime(0.12, t);
                        gain.gain.linearRampToValueAtTime(0.01, t + 0.05);
                        osc.start(t);
                        osc.stop(t + 0.05);
                    } else if (type === 'merge' || type === 'point' || type === 'score') {
                        osc.frequency.setValueAtTime(523.25, t); // C5
                        osc.frequency.setValueAtTime(659.25, t + 0.06); // E5
                        osc.frequency.setValueAtTime(783.99, t + 0.12); // G5
                        gain.gain.setValueAtTime(0.18, t);
                        gain.gain.linearRampToValueAtTime(0.01, t + 0.22);
                        osc.start(t);
                        osc.stop(t + 0.22);
                    } else if (type === 'win') {
                        osc.frequency.setValueAtTime(440, t);
                        osc.frequency.setValueAtTime(554.37, t + 0.08);
                        osc.frequency.setValueAtTime(659.25, t + 0.16);
                        osc.frequency.setValueAtTime(880, t + 0.24);
                        gain.gain.setValueAtTime(0.2, t);
                        gain.gain.linearRampToValueAtTime(0.01, t + 0.4);
                        osc.start(t);
                        osc.stop(t + 0.4);
                    } else if (type === 'over' || type === 'boom') {
                        osc.type = 'sawtooth';
                        osc.frequency.setValueAtTime(220, t);
                        osc.frequency.exponentialRampToValueAtTime(55, t + 0.25);
                        gain.gain.setValueAtTime(0.25, t);
                        gain.gain.linearRampToValueAtTime(0.01, t + 0.25);
                        osc.start(t);
                        osc.stop(t + 0.25);
                    }
                } catch(e) {}
            }
        };

        // Safe Storage Helper to prevent security sandboxing crashes
        const SafeStorage = {
            getItem(k) {
                try { return window.localStorage ? window.localStorage.getItem(k) : null; } catch(e) { return null; }
            },
            setItem(k, v) {
                try { if (window.localStorage) window.localStorage.setItem(k, v); } catch(e) {}
            }
        };

        // Universal Mobile Touch-Repeat & Zero-Latency Action Helper
        function bindTouchRepeat(el, callback, repeatMs = 120, delayMs = 240) {
            if (!el) return;
            let timer = null;
            let interval = null;
            const start = (e) => {
                if (e) e.preventDefault();
                AudioFX.init();
                AudioFX.vibrate(10);
                el.classList.add('touch-active');
                callback();
                timer = setTimeout(() => {
                    interval = setInterval(() => {
                        AudioFX.vibrate(8);
                        callback();
                    }, repeatMs);
                }, delayMs);
            };
            const stop = () => {
                el.classList.remove('touch-active');
                if (timer) clearTimeout(timer);
                if (interval) clearInterval(interval);
                timer = null;
                interval = null;
            };
            el.addEventListener('touchstart', start, { passive: false });
            el.addEventListener('touchend', stop);
            el.addEventListener('touchcancel', stop);
            el.addEventListener('mousedown', (e) => {
                if ('ontouchstart' in window) return;
                start(e);
            });
            el.addEventListener('mouseup', stop);
            el.addEventListener('mouseleave', stop);
        }

        window.addEventListener('click', () => AudioFX.init(), { once: true });
        window.addEventListener('touchstart', () => AudioFX.init(), { once: true });
    </script>
    """

    fun getHtmlForGame(gameId: String): String {
        return when (gameId) {
            "2048" -> get2048Html()
            "snake" -> getSnakeHtml()
            "tetris" -> getTetrisHtml()
            "minesweeper" -> getMinesweeperHtml()
            "sudoku" -> getSudokuHtml()
            "glider" -> getGliderHtml()
            "solitaire" -> getSolitaireHtml()
            "connect4" -> getConnect4Html()
            "memory" -> getMemoryHtml()
            "breakout" -> getBreakoutHtml()
            "slicer" -> getSlicerHtml()
            "crossing" -> getCrossingHtml()
            "jetpack" -> getJetpackHtml()
            "hillclimb" -> getHillClimbHtml()
            "altos" -> getAltosSlideHtml()
            else -> get2048Html()
        }
    }

    // 1. GAME: 2048
    private fun get2048Html(): String = """
    <!DOCTYPE html>
    <html>
    <head>
        <title>2048 Cyber</title>
        $COMMON_HEAD
        <style>
            .container { display: flex; flex-direction: column; align-items: center; justify-content: center; height: calc(100% - 50px); padding: 12px; }
            .grid {
                display: grid;
                grid-template-columns: repeat(4, 1fr);
                gap: 8px;
                background: #131A29;
                padding: 10px;
                border-radius: 12px;
                border: 2px solid #1E293B;
                width: 100%;
                max-width: 380px;
                aspect-ratio: 1 / 1;
                touch-action: none;
            }
            .cell {
                background: #1E293B;
                border-radius: 8px;
                display: flex;
                align-items: center;
                justify-content: center;
                font-weight: 800;
                font-size: 22px;
                color: #F8FAFC;
                transition: transform 0.1s ease, background-color 0.1s ease;
            }
            .val-2 { background: #334155; color: #E2E8F0; }
            .val-4 { background: #475569; color: #F1F5F9; }
            .val-8 { background: #0284C7; color: #FFFFFF; box-shadow: 0 0 10px rgba(2,132,199,0.4); }
            .val-16 { background: #0369A1; color: #FFFFFF; box-shadow: 0 0 12px rgba(3,105,161,0.5); }
            .val-32 { background: #2563EB; color: #FFFFFF; box-shadow: 0 0 14px rgba(37,99,235,0.6); }
            .val-64 { background: #4F46E5; color: #FFFFFF; box-shadow: 0 0 16px rgba(79,70,229,0.7); }
            .val-128 { background: #7C3AED; color: #FFFFFF; font-size: 20px; box-shadow: 0 0 18px rgba(124,58,237,0.7); }
            .val-256 { background: #9333EA; color: #FFFFFF; font-size: 20px; box-shadow: 0 0 20px rgba(147,51,234,0.8); }
            .val-512 { background: #C026D3; color: #FFFFFF; font-size: 18px; box-shadow: 0 0 22px rgba(192,38,211,0.8); }
            .val-1024 { background: #DB2777; color: #FFFFFF; font-size: 16px; box-shadow: 0 0 24px rgba(219,39,119,0.9); }
            .val-2048 { background: #E11D48; color: #FFFFFF; font-size: 16px; box-shadow: 0 0 30px #E11D48; animation: pulse 1s infinite alternate; }
            @keyframes pulse { from { transform: scale(1); } to { transform: scale(1.04); } }
            .dpad {
                display: grid;
                grid-template-columns: repeat(3, 54px);
                gap: 6px;
                margin-top: 14px;
            }
            .dbtn { height: 44px; font-size: 18px; border-radius: 8px; }
        </style>
    </head>
    <body>
        <div class="hud">
            <div>
                <span style="font-weight:bold; color:#94A3B8;">SCORE: </span>
                <span id="score" class="badge">0</span>
            </div>
            <div>
                <span style="font-weight:bold; color:#94A3B8;">BEST: </span>
                <span id="best" class="badge">0</span>
            </div>
            <button class="btn" onclick="restart()">New Game</button>
        </div>
        <div class="container">
            <div id="grid" class="grid"></div>
            <div class="dpad">
                <div></div>
                <button class="btn dbtn" onclick="move('up')">▲</button>
                <div></div>
                <button class="btn dbtn" onclick="move('left')">◀</button>
                <button class="btn dbtn" onclick="move('down')">▼</button>
                <button class="btn dbtn" onclick="move('right')">▶</button>
            </div>
            <div style="font-size:12px; color:#64748B; margin-top:8px;">Swipe or use D-pad to slide tiles</div>
        </div>

        <script>
            let board = Array(16).fill(0);
            let score = 0;
            let best = parseInt(SafeStorage.getItem('2048_best') || '0', 10);
            document.getElementById('best').innerText = best;

            function init() {
                board = Array(16).fill(0);
                score = 0;
                updateHud();
                addTile();
                addTile();
                render();
            }

            function updateHud() {
                document.getElementById('score').innerText = score;
                if (score > best) {
                    best = score;
                    SafeStorage.setItem('2048_best', best);
                    document.getElementById('best').innerText = best;
                }
            }

            function addTile() {
                const empty = [];
                board.forEach((v, i) => { if (v === 0) empty.push(i); });
                if (empty.length > 0) {
                    const idx = empty[Math.floor(Math.random() * empty.length)];
                    board[idx] = Math.random() < 0.9 ? 2 : 4;
                }
            }

            function render() {
                const grid = document.getElementById('grid');
                grid.innerHTML = '';
                board.forEach(val => {
                    const cell = document.createElement('div');
                    cell.className = 'cell' + (val > 0 ? ' val-' + val : '');
                    cell.innerText = val > 0 ? val : '';
                    grid.appendChild(cell);
                });
            }

            function slide(row) {
                let filtered = row.filter(x => x !== 0);
                let mergedScore = 0;
                for (let i = 0; i < filtered.length - 1; i++) {
                    if (filtered[i] === filtered[i + 1]) {
                        filtered[i] *= 2;
                        mergedScore += filtered[i];
                        filtered[i + 1] = 0;
                    }
                }
                filtered = filtered.filter(x => x !== 0);
                while (filtered.length < 4) filtered.push(0);
                return { row: filtered, gained: mergedScore };
            }

            function move(dir) {
                let moved = false;
                let gained = 0;
                let newBoard = [...board];

                if (dir === 'left' || dir === 'right') {
                    for (let r = 0; r < 4; r++) {
                        let row = [newBoard[r*4], newBoard[r*4+1], newBoard[r*4+2], newBoard[r*4+3]];
                        if (dir === 'right') row.reverse();
                        const res = slide(row);
                        if (dir === 'right') res.row.reverse();
                        gained += res.gained;
                        for (let c = 0; c < 4; c++) {
                            if (newBoard[r*4+c] !== res.row[c]) moved = true;
                            newBoard[r*4+c] = res.row[c];
                        }
                    }
                } else if (dir === 'up' || dir === 'down') {
                    for (let c = 0; c < 4; c++) {
                        let col = [newBoard[c], newBoard[4+c], newBoard[8+c], newBoard[12+c]];
                        if (dir === 'down') col.reverse();
                        const res = slide(col);
                        if (dir === 'down') res.row.reverse();
                        gained += res.gained;
                        for (let r = 0; r < 4; r++) {
                            if (newBoard[r*4+c] !== res.row[r]) moved = true;
                            newBoard[r*4+c] = res.row[r];
                        }
                    }
                }

                if (moved) {
                    board = newBoard;
                    score += gained;
                    if (gained > 0) AudioFX.play('merge'); else AudioFX.play('move');
                    updateHud();
                    addTile();
                    render();
                }
            }

            function restart() {
                init();
                AudioFX.play('tap');
            }

            // Touch Swipe Handler
            let touchStartX = 0, touchStartY = 0;
            const gridEl = document.getElementById('grid');
            gridEl.addEventListener('touchstart', e => {
                touchStartX = e.touches[0].clientX;
                touchStartY = e.touches[0].clientY;
            }, { passive: true });
            gridEl.addEventListener('touchend', e => {
                const dx = e.changedTouches[0].clientX - touchStartX;
                const dy = e.changedTouches[0].clientY - touchStartY;
                const absDx = Math.abs(dx);
                const absDy = Math.abs(dy);
                if (Math.max(absDx, absDy) > 25) {
                    if (absDx > absDy) {
                        move(dx > 0 ? 'right' : 'left');
                    } else {
                        move(dy > 0 ? 'down' : 'up');
                    }
                }
            }, { passive: true });

            window.addEventListener('keydown', e => {
                if (e.key === 'ArrowUp') move('up');
                else if (e.key === 'ArrowDown') move('down');
                else if (e.key === 'ArrowLeft') move('left');
                else if (e.key === 'ArrowRight') move('right');
            });

            init();
        </script>
    </body>
    </html>
    """.trimIndent()

    // 2. GAME: RETRO SNAKE
    private fun getSnakeHtml(): String = """
    <!DOCTYPE html>
    <html>
    <head>
        <title>Retro Snake</title>
        $COMMON_HEAD
        <style>
            .container { display: flex; flex-direction: column; align-items: center; justify-content: center; height: calc(100% - 50px); padding: 8px; }
            #canvas {
                background: #0D131F;
                border: 2px solid #1E293B;
                border-radius: 12px;
                box-shadow: 0 0 16px rgba(56, 189, 248, 0.15);
                max-width: 95vw;
                max-height: 52vh;
                touch-action: none;
            }
            .dpad {
                display: grid;
                grid-template-columns: repeat(3, 68px);
                gap: 10px;
                margin-top: 14px;
            }
            .dbtn { height: 56px; font-size: 24px; border-radius: 14px; background: #1E293B; }
        </style>
    </head>
    <body>
        <div class="hud">
            <div><span style="color:#94A3B8;">SCORE: </span><span id="score" class="badge">0</span></div>
            <div><span style="color:#94A3B8;">BEST: </span><span id="best" class="badge">0</span></div>
            <button class="btn" onclick="resetGame()">Restart</button>
        </div>
        <div class="container">
            <canvas id="canvas" width="400" height="400"></canvas>
            <div class="dpad">
                <div></div>
                <button class="btn dbtn" ontouchstart="setDir(0, -1); event.preventDefault();" onclick="setDir(0, -1)">▲</button>
                <div></div>
                <button class="btn dbtn" ontouchstart="setDir(-1, 0); event.preventDefault();" onclick="setDir(-1, 0)">◀</button>
                <button class="btn dbtn" ontouchstart="setDir(0, 1); event.preventDefault();" onclick="setDir(0, 1)">▼</button>
                <button class="btn dbtn" ontouchstart="setDir(1, 0); event.preventDefault();" onclick="setDir(1, 0)">▶</button>
            </div>
        </div>

        <script>
            const canvas = document.getElementById('canvas');
            const ctx = canvas.getContext('2d');
            const GRID = 20;
            const COLS = canvas.width / GRID;
            const ROWS = canvas.height / GRID;

            let snake = [{x: 10, y: 10}, {x: 9, y: 10}, {x: 8, y: 10}];
            let food = {x: 15, y: 10};
            let dir = {x: 1, y: 0};
            let nextDir = {x: 1, y: 0};
            let score = 0;
            let best = parseInt(SafeStorage.getItem('snake_best') || '0', 10);
            let gameOver = false;
            let gameLoop = null;

            document.getElementById('best').innerText = best;

            function spawnFood() {
                while (true) {
                    const fx = Math.floor(Math.random() * COLS);
                    const fy = Math.floor(Math.random() * ROWS);
                    if (!snake.some(s => s.x === fx && s.y === fy)) {
                        food = {x: fx, y: fy};
                        break;
                    }
                }
            }

            function setDir(dx, dy) {
                if (gameOver) { resetGame(); return; }
                if (dx !== -dir.x || dy !== -dir.y) {
                    nextDir = {x: dx, y: dy};
                    AudioFX.play('move');
                }
            }

            function step() {
                if (gameOver) return;
                dir = nextDir;
                const head = {x: snake[0].x + dir.x, y: snake[0].y + dir.y};

                if (head.x < 0 || head.x >= COLS || head.y < 0 || head.y >= ROWS || snake.some(s => s.x === head.x && s.y === head.y)) {
                    gameOver = true;
                    AudioFX.play('over');
                    draw();
                    return;
                }

                snake.unshift(head);
                if (head.x === food.x && head.y === food.y) {
                    score += 10;
                    if (score > best) {
                        best = score;
                        SafeStorage.setItem('snake_best', best);
                        document.getElementById('best').innerText = best;
                    }
                    document.getElementById('score').innerText = score;
                    AudioFX.play('score');
                    spawnFood();
                } else {
                    snake.pop();
                }
                draw();
            }

            function draw() {
                ctx.fillStyle = '#0D131F';
                ctx.fillRect(0, 0, canvas.width, canvas.height);

                // Grid pattern
                ctx.strokeStyle = '#131D2E';
                ctx.lineWidth = 1;
                for (let x = 0; x < canvas.width; x += GRID) {
                    ctx.beginPath(); ctx.moveTo(x, 0); ctx.lineTo(x, canvas.height); ctx.stroke();
                }
                for (let y = 0; y < canvas.height; y += GRID) {
                    ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(canvas.width, y); ctx.stroke();
                }

                // Food
                ctx.fillStyle = '#F43F5E';
                ctx.shadowColor = '#F43F5E';
                ctx.shadowBlur = 10;
                ctx.beginPath();
                ctx.arc(food.x * GRID + GRID/2, food.y * GRID + GRID/2, GRID/2 - 2, 0, Math.PI * 2);
                ctx.fill();
                ctx.shadowBlur = 0;

                // Snake
                snake.forEach((s, idx) => {
                    ctx.fillStyle = idx === 0 ? '#38BDF8' : '#0284C7';
                    if (idx === 0) {
                        ctx.shadowColor = '#38BDF8';
                        ctx.shadowBlur = 8;
                    } else {
                        ctx.shadowBlur = 0;
                    }
                    ctx.fillRect(s.x * GRID + 1, s.y * GRID + 1, GRID - 2, GRID - 2);
                });
                ctx.shadowBlur = 0;

                if (gameOver) {
                    ctx.fillStyle = 'rgba(9, 13, 22, 0.85)';
                    ctx.fillRect(0, 0, canvas.width, canvas.height);
                    ctx.fillStyle = '#F43F5E';
                    ctx.font = 'bold 28px sans-serif';
                    ctx.textAlign = 'center';
                    ctx.fillText('GAME OVER', canvas.width / 2, canvas.height / 2 - 10);
                    ctx.fillStyle = '#94A3B8';
                    ctx.font = '16px sans-serif';
                    ctx.fillText('Tap D-pad or Restart to play again', canvas.width / 2, canvas.height / 2 + 25);
                }
            }

            function resetGame() {
                snake = [{x: 10, y: 10}, {x: 9, y: 10}, {x: 8, y: 10}];
                dir = {x: 1, y: 0};
                nextDir = {x: 1, y: 0};
                score = 0;
                gameOver = false;
                document.getElementById('score').innerText = 0;
                spawnFood();
                draw();
            }

            // Swipe anywhere on screen or canvas
            let tsX = 0, tsY = 0;
            document.body.addEventListener('touchstart', e => {
                if (e.target.closest('.dpad') || e.target.closest('.hud')) return;
                tsX = e.touches[0].clientX;
                tsY = e.touches[0].clientY;
            }, { passive: true });
            document.body.addEventListener('touchend', e => {
                if (e.target.closest('.dpad') || e.target.closest('.hud')) return;
                const dx = e.changedTouches[0].clientX - tsX;
                const dy = e.changedTouches[0].clientY - tsY;
                if (Math.max(Math.abs(dx), Math.abs(dy)) > 15) {
                    if (Math.abs(dx) > Math.abs(dy)) setDir(dx > 0 ? 1 : -1, 0);
                    else setDir(0, dy > 0 ? 1 : -1);
                }
            }, { passive: true });

            window.addEventListener('keydown', e => {
                if (e.key === 'ArrowUp') setDir(0, -1);
                else if (e.key === 'ArrowDown') setDir(0, 1);
                else if (e.key === 'ArrowLeft') setDir(-1, 0);
                else if (e.key === 'ArrowRight') setDir(1, 0);
            });

            resetGame();
            setInterval(step, 115);
        </script>
    </body>
    </html>
    """.trimIndent()

    // 3. GAME: BLOCK DROP (TETRIS)
    private fun getTetrisHtml(): String = """
    <!DOCTYPE html>
    <html>
    <head>
        <title>Block Drop</title>
        $COMMON_HEAD
        <style>
            .container { display: flex; flex-direction: column; align-items: center; justify-content: center; height: calc(100% - 50px); padding: 4px; }
            .board-row { display: flex; align-items: flex-start; justify-content: center; gap: 8px; }
            #tetris-canvas {
                background: #0D131F;
                border: 2px solid #1E293B;
                border-radius: 8px;
                box-shadow: 0 0 16px rgba(56, 189, 248, 0.12);
                height: 52vh;
                width: auto;
                aspect-ratio: 10 / 20;
            }
            .side-panel { display: flex; flex-direction: column; gap: 8px; width: 70px; }
            .preview-box { background: #131A29; border: 1px solid #1E293B; border-radius: 6px; padding: 4px; text-align: center; }
            .controls-grid {
                display: grid;
                grid-template-columns: repeat(5, 1fr);
                gap: 6px;
                width: 100%;
                max-width: 380px;
                margin-top: 10px;
            }
            .cbtn { height: 48px; font-size: 18px; border-radius: 8px; font-weight: bold; }
        </style>
    </head>
    <body>
        <div class="hud">
            <div><span style="color:#94A3B8;">SCORE: </span><span id="score" class="badge">0</span></div>
            <div><span style="color:#94A3B8;">LINES: </span><span id="lines" class="badge">0</span></div>
            <button class="btn" onclick="resetGame()">New</button>
        </div>
        <div class="container">
            <div class="board-row">
                <canvas id="tetris-canvas" width="200" height="400"></canvas>
                <div class="side-panel">
                    <div class="preview-box">
                        <div style="font-size:10px; color:#64748B; font-weight:bold;">NEXT</div>
                        <canvas id="next-canvas" width="60" height="60"></canvas>
                    </div>
                    <div class="preview-box">
                        <div style="font-size:10px; color:#64748B; font-weight:bold;">HOLD</div>
                        <canvas id="hold-canvas" width="60" height="60"></canvas>
                    </div>
                </div>
            </div>
            <div class="controls-grid">
                <button id="btn-hold" class="btn cbtn" onclick="holdPiece()">📥</button>
                <button id="btn-left" class="btn cbtn">◀</button>
                <button id="btn-rot" class="btn cbtn" onclick="rotatePiece()">↻</button>
                <button id="btn-right" class="btn cbtn">▶</button>
                <button id="btn-hard" class="btn cbtn" onclick="hardDrop()" style="color:#F43F5E;">⚡</button>
            </div>
            <button id="btn-drop" class="btn" style="width:100%; max-width:380px; margin-top:8px; height:48px; font-weight:bold;">▼ SOFT DROP</button>
        </div>

        <script>
            const canvas = document.getElementById('tetris-canvas');
            const ctx = canvas.getContext('2d');
            const nextCanvas = document.getElementById('next-canvas');
            const nextCtx = nextCanvas.getContext('2d');
            const holdCanvas = document.getElementById('hold-canvas');
            const holdCtx = holdCanvas.getContext('2d');

            const COLS = 10;
            const ROWS = 20;
            const BLOCK = 20;

            const SHAPES = {
                I: { color: '#06B6D4', matrix: [[1,1,1,1]] },
                J: { color: '#3B82F6', matrix: [[1,0,0],[1,1,1]] },
                L: { color: '#F97316', matrix: [[0,0,1],[1,1,1]] },
                O: { color: '#EAB308', matrix: [[1,1],[1,1]] },
                S: { color: '#22C55E', matrix: [[0,1,1],[1,1,0]] },
                T: { color: '#A855F7', matrix: [[0,1,0],[1,1,1]] },
                Z: { color: '#EF4444', matrix: [[1,1,0],[0,1,1]] }
            };
            const PIECES = ['I','J','L','O','S','T','Z'];

            let grid = Array.from({length: ROWS}, () => Array(COLS).fill(0));
            let current = null;
            let currentX = 0, currentY = 0;
            let nextPiece = null;
            let holdType = null;
            let canHold = true;
            let score = 0, lines = 0;
            let dropTimer = null;
            let gameOver = false;

            function randomPiece() {
                const type = PIECES[Math.floor(Math.random() * PIECES.length)];
                return { type, matrix: SHAPES[type].matrix, color: SHAPES[type].color };
            }

            function spawnPiece() {
                current = nextPiece || randomPiece();
                nextPiece = randomPiece();
                currentX = Math.floor((COLS - current.matrix[0].length) / 2);
                currentY = 0;
                canHold = true;
                if (collides(current.matrix, currentX, currentY)) {
                    gameOver = true;
                    AudioFX.play('over');
                }
                drawNext();
            }

            function collides(matrix, ox, oy) {
                for (let r = 0; r < matrix.length; r++) {
                    for (let c = 0; c < matrix[r].length; c++) {
                        if (matrix[r][c]) {
                            const nx = ox + c;
                            const ny = oy + r;
                            if (nx < 0 || nx >= COLS || ny >= ROWS) return true;
                            if (ny >= 0 && grid[ny][nx] !== 0) return true;
                        }
                    }
                }
                return false;
            }

            function rotate(matrix) {
                return matrix[0].map((_, i) => matrix.map(row => row[i]).reverse());
            }

            function rotatePiece() {
                if (gameOver || !current) return;
                const rotated = rotate(current.matrix);
                if (!collides(rotated, currentX, currentY)) {
                    current.matrix = rotated;
                    AudioFX.play('move');
                }
                draw();
            }

            function moveLeft() {
                if (gameOver || !current) return;
                if (!collides(current.matrix, currentX - 1, currentY)) {
                    currentX--;
                    AudioFX.play('move');
                }
                draw();
            }

            function moveRight() {
                if (gameOver || !current) return;
                if (!collides(current.matrix, currentX + 1, currentY)) {
                    currentX++;
                    AudioFX.play('move');
                }
                draw();
            }

            function softDrop() {
                if (gameOver || !current) return;
                if (!collides(current.matrix, currentX, currentY + 1)) {
                    currentY++;
                } else {
                    lockPiece();
                }
                draw();
            }

            function hardDrop() {
                if (gameOver || !current) return;
                while (!collides(current.matrix, currentX, currentY + 1)) {
                    currentY++;
                }
                AudioFX.play('tap');
                lockPiece();
                draw();
            }

            function holdPiece() {
                if (gameOver || !canHold || !current) return;
                canHold = false;
                const prevType = current.type;
                if (holdType === null) {
                    holdType = prevType;
                    spawnPiece();
                } else {
                    const temp = holdType;
                    holdType = prevType;
                    current = { type: temp, matrix: SHAPES[temp].matrix, color: SHAPES[temp].color };
                    currentX = Math.floor((COLS - current.matrix[0].length) / 2);
                    currentY = 0;
                }
                AudioFX.play('tap');
                drawHold();
                draw();
            }

            function lockPiece() {
                current.matrix.forEach((row, r) => {
                    row.forEach((v, c) => {
                        if (v && currentY + r >= 0) grid[currentY + r][currentX + c] = current.color;
                    });
                });

                // Clear full lines
                let cleared = 0;
                for (let r = ROWS - 1; r >= 0; r--) {
                    if (grid[r].every(c => c !== 0)) {
                        grid.splice(r, 1);
                        grid.unshift(Array(COLS).fill(0));
                        cleared++;
                        r++;
                    }
                }
                if (cleared > 0) {
                    lines += cleared;
                    score += cleared === 1 ? 100 : cleared === 2 ? 300 : cleared === 3 ? 500 : 800;
                    document.getElementById('score').innerText = score;
                    document.getElementById('lines').innerText = lines;
                    AudioFX.play('win');
                }

                spawnPiece();
            }

            function draw() {
                ctx.fillStyle = '#0D131F';
                ctx.fillRect(0, 0, canvas.width, canvas.height);

                // Grid lines
                ctx.strokeStyle = '#162235';
                for (let r = 0; r < ROWS; r++) {
                    ctx.beginPath(); ctx.moveTo(0, r * BLOCK); ctx.lineTo(canvas.width, r * BLOCK); ctx.stroke();
                }
                for (let c = 0; c < COLS; c++) {
                    ctx.beginPath(); ctx.moveTo(c * BLOCK, 0); ctx.lineTo(c * BLOCK, canvas.height); ctx.stroke();
                }

                // Locked blocks
                for (let r = 0; r < ROWS; r++) {
                    for (let c = 0; c < COLS; c++) {
                        if (grid[r][c]) {
                            ctx.fillStyle = grid[r][c];
                            ctx.fillRect(c * BLOCK + 1, r * BLOCK + 1, BLOCK - 2, BLOCK - 2);
                        }
                    }
                }

                // Current piece
                if (current && !gameOver) {
                    ctx.fillStyle = current.color;
                    current.matrix.forEach((row, r) => {
                        row.forEach((v, c) => {
                            if (v) ctx.fillRect((currentX + c) * BLOCK + 1, (currentY + r) * BLOCK + 1, BLOCK - 2, BLOCK - 2);
                        });
                    });
                }

                if (gameOver) {
                    ctx.fillStyle = 'rgba(9, 13, 22, 0.85)';
                    ctx.fillRect(0, 0, canvas.width, canvas.height);
                    ctx.fillStyle = '#EF4444';
                    ctx.font = 'bold 20px sans-serif';
                    ctx.textAlign = 'center';
                    ctx.fillText('GAME OVER', canvas.width / 2, canvas.height / 2);
                }
            }

            function drawPreview(cCtx, pType) {
                cCtx.fillStyle = '#131A29';
                cCtx.fillRect(0, 0, 60, 60);
                if (!pType) return;
                const p = SHAPES[pType];
                cCtx.fillStyle = p.color;
                const bs = 12;
                const ox = (60 - p.matrix[0].length * bs) / 2;
                const oy = (60 - p.matrix.length * bs) / 2;
                p.matrix.forEach((row, r) => {
                    row.forEach((v, c) => {
                        if (v) cCtx.fillRect(ox + c * bs, oy + r * bs, bs - 1, bs - 1);
                    });
                });
            }

            function drawNext() { drawPreview(nextCtx, nextPiece.type); }
            function drawHold() { drawPreview(holdCtx, holdType); }

            function resetGame() {
                grid = Array.from({length: ROWS}, () => Array(COLS).fill(0));
                score = 0; lines = 0; holdType = null; gameOver = false;
                document.getElementById('score').innerText = 0;
                document.getElementById('lines').innerText = 0;
                nextPiece = randomPiece();
                spawnPiece();
                drawHold();
                draw();
            }

            // Mobile Touch Repeat Controls
            bindTouchRepeat(document.getElementById('btn-left'), () => moveLeft(), 90, 220);
            bindTouchRepeat(document.getElementById('btn-right'), () => moveRight(), 90, 220);
            bindTouchRepeat(document.getElementById('btn-drop'), () => softDrop(), 70, 180);

            // Direct Touch Gestures on Tetris Canvas
            let tStartX = 0, tStartY = 0, tStartTime = 0;
            canvas.addEventListener('touchstart', e => {
                tStartX = e.touches[0].clientX;
                tStartY = e.touches[0].clientY;
                tStartTime = Date.now();
            }, { passive: true });
            canvas.addEventListener('touchend', e => {
                const dx = e.changedTouches[0].clientX - tStartX;
                const dy = e.changedTouches[0].clientY - tStartY;
                const elapsed = Date.now() - tStartTime;
                const absDx = Math.abs(dx);
                const absDy = Math.abs(dy);

                if (absDx < 18 && absDy < 18 && elapsed < 250) {
                    rotatePiece();
                } else if (absDx > absDy && absDx > 25) {
                    if (dx > 0) moveRight(); else moveLeft();
                } else if (absDy > absDx && absDy > 30) {
                    if (dy > 80) hardDrop(); else softDrop();
                }
            }, { passive: true });

            resetGame();
            setInterval(() => { if (!gameOver) softDrop(); }, 750);
        </script>
    </body>
    </html>
    """.trimIndent()

    // 4. GAME: MINESWEEPER
    private fun getMinesweeperHtml(): String = """
    <!DOCTYPE html>
    <html>
    <head>
        <title>Minesweeper</title>
        $COMMON_HEAD
        <style>
            .container { display: flex; flex-direction: column; align-items: center; justify-content: center; height: calc(100% - 50px); padding: 8px; }
            .board {
                display: grid;
                gap: 2px;
                background: #1E293B;
                padding: 6px;
                border-radius: 8px;
                max-width: 95vw;
                max-height: 60vh;
            }
            .cell {
                background: #334155;
                color: #F8FAFC;
                border-radius: 4px;
                display: flex;
                align-items: center;
                justify-content: center;
                font-weight: bold;
                font-size: 15px;
                cursor: pointer;
            }
            .cell.revealed { background: #0F172A; }
            .mode-switch {
                display: flex;
                gap: 8px;
                margin-top: 12px;
            }
            .c-1 { color: #38BDF8; } .c-2 { color: #4ADE80; } .c-3 { color: #F87171; }
            .c-4 { color: #A78BFA; } .c-5 { color: #FBBF24; }
        </style>
    </head>
    <body>
        <div class="hud">
            <div><span style="color:#94A3B8;">💣: </span><span id="mines-left" class="badge">10</span></div>
            <button id="face" class="btn" onclick="initGame()" style="font-size:18px;">🙂</button>
            <div><span style="color:#94A3B8;">TIME: </span><span id="timer" class="badge">0</span></div>
        </div>
        <div class="container">
            <div id="board" class="board"></div>
            <div class="mode-switch">
                <button id="dig-btn" class="btn" style="background:#38BDF8; color:#090D16;" onclick="setMode('dig')">⛏️ Dig Mode</button>
                <button id="flag-btn" class="btn" onclick="setMode('flag')">🚩 Flag Mode</button>
            </div>
            <div style="font-size:11px; color:#64748B; margin-top:6px;">Tap to act / Long-press to toggle flag</div>
        </div>

        <script>
            const ROWS = 9, COLS = 9, MINES = 10;
            let board = [];
            let mode = 'dig'; // 'dig' or 'flag'
            let gameOver = false;
            let timer = 0, timerId = null;

            function initGame() {
                clearInterval(timerId);
                timer = 0; gameOver = false;
                document.getElementById('timer').innerText = 0;
                document.getElementById('face').innerText = '🙂';
                document.getElementById('mines-left').innerText = MINES;

                board = Array.from({length: ROWS}, () => Array.from({length: COLS}, () => ({
                    mine: false, revealed: false, flagged: false, count: 0
                })));

                // Plant mines
                let planted = 0;
                while (planted < MINES) {
                    const r = Math.floor(Math.random() * ROWS);
                    const c = Math.floor(Math.random() * COLS);
                    if (!board[r][c].mine) {
                        board[r][c].mine = true;
                        planted++;
                    }
                }

                // Calculate counts
                for (let r = 0; r < ROWS; r++) {
                    for (let c = 0; c < COLS; c++) {
                        if (!board[r][c].mine) {
                            let n = 0;
                            for (let dr = -1; dr <= 1; dr++) {
                                for (let dc = -1; dc <= 1; dc++) {
                                    const nr = r + dr, nc = c + dc;
                                    if (nr >= 0 && nr < ROWS && nc >= 0 && nc < COLS && board[nr][nc].mine) n++;
                                }
                            }
                            board[r][c].count = n;
                        }
                    }
                }

                render();
            }

            function setMode(m) {
                mode = m;
                document.getElementById('dig-btn').style.background = m === 'dig' ? '#38BDF8' : '#1E293B';
                document.getElementById('dig-btn').style.color = m === 'dig' ? '#090D16' : '#38BDF8';
                document.getElementById('flag-btn').style.background = m === 'flag' ? '#38BDF8' : '#1E293B';
                document.getElementById('flag-btn').style.color = m === 'flag' ? '#090D16' : '#38BDF8';
                AudioFX.play('tap');
            }

            function clickCell(r, c) {
                if (gameOver || board[r][c].revealed) return;
                if (!timerId) timerId = setInterval(() => { timer++; document.getElementById('timer').innerText = timer; }, 1000);

                if (mode === 'flag') {
                    toggleFlag(r, c);
                    return;
                }

                if (board[r][c].flagged) return;

                if (board[r][c].mine) {
                    gameOver = true;
                    clearInterval(timerId);
                    document.getElementById('face').innerText = '😵';
                    AudioFX.play('boom');
                    revealAll();
                    return;
                }

                reveal(r, c);
                AudioFX.play('move');
                checkWin();
                render();
            }

            function toggleFlag(r, c) {
                if (board[r][c].revealed) return;
                board[r][c].flagged = !board[r][c].flagged;
                AudioFX.play('tap');
                const flags = board.flat().filter(x => x.flagged).length;
                document.getElementById('mines-left').innerText = MINES - flags;
                render();
            }

            function reveal(r, c) {
                if (r < 0 || r >= ROWS || c < 0 || c >= COLS || board[r][c].revealed || board[r][c].flagged) return;
                board[r][c].revealed = true;
                if (board[r][c].count === 0 && !board[r][c].mine) {
                    for (let dr = -1; dr <= 1; dr++) {
                        for (let dc = -1; dc <= 1; dc++) reveal(r + dr, c + dc);
                    }
                }
            }

            function revealAll() {
                board.forEach(row => row.forEach(c => c.revealed = true));
                render();
            }

            function checkWin() {
                const unrevealedSafe = board.flat().some(c => !c.mine && !c.revealed);
                if (!unrevealedSafe) {
                    gameOver = true;
                    clearInterval(timerId);
                    document.getElementById('face').innerText = '😎';
                    AudioFX.play('win');
                }
            }

            function render() {
                const bEl = document.getElementById('board');
                bEl.style.gridTemplateColumns = 'repeat(' + COLS + ', 34px)';
                bEl.innerHTML = '';
                for (let r = 0; r < ROWS; r++) {
                    for (let c = 0; c < COLS; c++) {
                        const cell = board[r][c];
                        const div = document.createElement('div');
                        div.className = 'cell' + (cell.revealed ? ' revealed' : '');
                        div.style.width = '34px'; div.style.height = '34px';

                        if (cell.revealed) {
                            if (cell.mine) div.innerText = '💣';
                            else if (cell.count > 0) {
                                div.innerText = cell.count;
                                div.className += ' c-' + cell.count;
                            }
                        } else if (cell.flagged) {
                            div.innerText = '🚩';
                        }

                        div.onclick = () => clickCell(r, c);
                        div.oncontextmenu = (e) => { e.preventDefault(); toggleFlag(r, c); };
                        bEl.appendChild(div);
                    }
                }
            }

            initGame();
        </script>
    </body>
    </html>
    """.trimIndent()

    // 5. GAME: SUDOKU MASTER
    private fun getSudokuHtml(): String = """
    <!DOCTYPE html>
    <html>
    <head>
        <title>Sudoku Master</title>
        $COMMON_HEAD
        <style>
            .container { display: flex; flex-direction: column; align-items: center; justify-content: center; height: calc(100% - 50px); padding: 8px; }
            .grid {
                display: grid;
                grid-template-columns: repeat(9, 1fr);
                gap: 1px;
                background: #334155;
                padding: 3px;
                border-radius: 8px;
                width: 100%;
                max-width: 380px;
                aspect-ratio: 1 / 1;
            }
            .scell {
                background: #131A29;
                color: #F8FAFC;
                display: flex;
                align-items: center;
                justify-content: center;
                font-size: 16px;
                font-weight: bold;
                cursor: pointer;
            }
            .scell.selected { background: #0284C7 !important; color: #FFF; }
            .scell.same-num { background: #1E293B; }
            .scell.fixed { color: #38BDF8; font-weight: 900; }
            .scell.conflict { background: #7F1D1D !important; color: #FCA5A5; }
            .border-right { border-right: 2px solid #64748B; }
            .border-bottom { border-bottom: 2px solid #64748B; }
            .keypad {
                display: grid;
                grid-template-columns: repeat(5, 1fr);
                gap: 6px;
                width: 100%;
                max-width: 380px;
                margin-top: 10px;
            }
            .kbtn { height: 44px; font-size: 18px; border-radius: 6px; }
        </style>
    </head>
    <body>
        <div class="hud">
            <div><span style="color:#94A3B8;">DIFF: </span><span id="diff" class="badge">Medium</span></div>
            <button class="btn" onclick="newGame()">New Game</button>
        </div>
        <div class="container">
            <div id="sudoku-grid" class="grid"></div>
            <div class="keypad">
                <button class="btn kbtn" onclick="inputNum(1)">1</button>
                <button class="btn kbtn" onclick="inputNum(2)">2</button>
                <button class="btn kbtn" onclick="inputNum(3)">3</button>
                <button class="btn kbtn" onclick="inputNum(4)">4</button>
                <button class="btn kbtn" onclick="inputNum(5)">5</button>
                <button class="btn kbtn" onclick="inputNum(6)">6</button>
                <button class="btn kbtn" onclick="inputNum(7)">7</button>
                <button class="btn kbtn" onclick="inputNum(8)">8</button>
                <button class="btn kbtn" onclick="inputNum(9)">9</button>
                <button class="btn kbtn" onclick="eraseCell()" style="color:#F43F5E;">⌫</button>
            </div>
        </div>

        <script>
            // Clean playable sample Sudoku puzzles
            const PUZZLES = [
                "530070000600195000098000060800060003400803001700020006060000280000419005000080079",
                "000260701680070090190004500820100040004602900050003028009300074040050036703018000",
                "100489006730000040000001295007120600500703008006095700914600000020000037800512004"
            ];
            let initial = Array(81).fill(0);
            let current = Array(81).fill(0);
            let selectedIdx = -1;

            function newGame() {
                const p = PUZZLES[Math.floor(Math.random() * PUZZLES.length)];
                initial = p.split('').map(x => parseInt(x, 10));
                current = [...initial];
                selectedIdx = 0;
                render();
                AudioFX.play('tap');
            }

            function selectCell(idx) {
                selectedIdx = idx;
                render();
                AudioFX.play('move');
            }

            function inputNum(n) {
                if (selectedIdx >= 0 && initial[selectedIdx] === 0) {
                    current[selectedIdx] = n;
                    AudioFX.play('tap');
                    render();
                    checkComplete();
                }
            }

            function eraseCell() {
                if (selectedIdx >= 0 && initial[selectedIdx] === 0) {
                    current[selectedIdx] = 0;
                    AudioFX.play('tap');
                    render();
                }
            }

            function checkComplete() {
                if (current.every(v => v !== 0)) {
                    AudioFX.play('win');
                }
            }

            function render() {
                const gridEl = document.getElementById('sudoku-grid');
                gridEl.innerHTML = '';
                const curVal = selectedIdx >= 0 ? current[selectedIdx] : 0;

                for (let i = 0; i < 81; i++) {
                    const r = Math.floor(i / 9);
                    const c = i % 9;
                    const val = current[i];
                    const div = document.createElement('div');
                    div.className = 'scell';
                    if (c === 2 || c === 5) div.className += ' border-right';
                    if (r === 2 || r === 5) div.className += ' border-bottom';

                    if (initial[i] !== 0) div.className += ' fixed';
                    if (i === selectedIdx) div.className += ' selected';
                    else if (val > 0 && val === curVal) div.className += ' same-num';

                    div.innerText = val > 0 ? val : '';
                    div.onclick = () => selectCell(i);
                    gridEl.appendChild(div);
                }
            }

            newGame();
        </script>
    </body>
    </html>
    """.trimIndent()

    // 6. GAME: CYBER GLIDER (FLAPPY)
    private fun getGliderHtml(): String = """
    <!DOCTYPE html>
    <html>
    <head>
        <title>Cyber Glider</title>
        $COMMON_HEAD
        <style>
            .container { display: flex; flex-direction: column; align-items: center; justify-content: center; height: calc(100% - 50px); padding: 4px; }
            #glider-canvas {
                background: #090D16;
                border: 2px solid #1E293B;
                border-radius: 12px;
                width: 100%;
                max-width: 400px;
                height: 70vh;
                touch-action: none;
            }
        </style>
    </head>
    <body>
        <div class="hud">
            <div><span style="color:#94A3B8;">SCORE: </span><span id="score" class="badge">0</span></div>
            <div><span style="color:#94A3B8;">BEST: </span><span id="best" class="badge">0</span></div>
            <button class="btn" onclick="flap()">Tap to Fly</button>
        </div>
        <div class="container">
            <canvas id="glider-canvas" width="360" height="540"></canvas>
            <div style="font-size:12px; color:#64748B; margin-top:6px;">Tap anywhere to boost glider upward</div>
        </div>

        <script>
            const canvas = document.getElementById('glider-canvas');
            const ctx = canvas.getContext('2d');

            let gliderY = 250;
            let velY = 0;
            const GRAVITY = 0.38;
            const FLAP = -6.8;

            let pipes = [];
            let score = 0;
            let best = parseInt(SafeStorage.getItem('glider_best') || '0', 10);
            let state = 'ready'; // 'ready', 'play', 'dead'
            let frame = 0;

            document.getElementById('best').innerText = best;

            function flap() {
                if (state === 'ready') {
                    state = 'play';
                    velY = FLAP;
                    AudioFX.play('move');
                } else if (state === 'play') {
                    velY = FLAP;
                    AudioFX.play('move');
                } else if (state === 'dead') {
                    reset();
                }
            }

            function reset() {
                gliderY = 250; velY = 0; pipes = []; score = 0; frame = 0;
                state = 'ready';
                document.getElementById('score').innerText = 0;
            }

            function update() {
                if (state === 'play') {
                    velY += GRAVITY;
                    gliderY += velY;
                    frame++;

                    // Spawn pipes
                    if (frame % 95 === 0) {
                        const gap = 135;
                        const top = Math.floor(Math.random() * (canvas.height - gap - 140)) + 40;
                        pipes.push({ x: canvas.width, top, bottom: top + gap, scored: false });
                    }

                    // Move pipes
                    for (let i = 0; i < pipes.length; i++) {
                        const p = pipes[i];
                        p.x -= 2.6;

                        // Collision check
                        const gx = 65, gy = gliderY, gw = 28, gh = 20;
                        if (gx + gw > p.x && gx < p.x + 50) {
                            if (gy < p.top || gy + gh > p.bottom) {
                                state = 'dead';
                                AudioFX.play('boom');
                            }
                        }

                        // Score
                        if (!p.scored && p.x + 50 < gx) {
                            p.scored = true;
                            score++;
                            AudioFX.play('score');
                            if (score > best) {
                                best = score;
                                SafeStorage.setItem('glider_best', best);
                                document.getElementById('best').innerText = best;
                            }
                            document.getElementById('score').innerText = score;
                        }
                    }

                    pipes = pipes.filter(p => p.x > -60);

                    // Floor/ceiling collision
                    if (gliderY < 0 || gliderY > canvas.height - 25) {
                        state = 'dead';
                        AudioFX.play('boom');
                    }
                }
            }

            function draw() {
                ctx.fillStyle = '#090D16';
                ctx.fillRect(0, 0, canvas.width, canvas.height);

                // Pipes
                pipes.forEach(p => {
                    ctx.fillStyle = '#1E293B';
                    ctx.strokeStyle = '#38BDF8';
                    ctx.lineWidth = 2;
                    // Top pipe
                    ctx.fillRect(p.x, 0, 50, p.top);
                    ctx.strokeRect(p.x, 0, 50, p.top);
                    // Bottom pipe
                    ctx.fillRect(p.x, p.bottom, 50, canvas.height - p.bottom);
                    ctx.strokeRect(p.x, p.bottom, 50, canvas.height - p.bottom);
                });

                // Glider (triangle ship)
                ctx.save();
                ctx.translate(65 + 14, gliderY + 10);
                const rot = Math.min(Math.PI / 4, Math.max(-Math.PI / 4, velY * 0.08));
                ctx.rotate(rot);
                ctx.fillStyle = '#38BDF8';
                ctx.shadowColor = '#38BDF8';
                ctx.shadowBlur = 10;
                ctx.beginPath();
                ctx.moveTo(14, 0);
                ctx.lineTo(-14, -10);
                ctx.lineTo(-8, 0);
                ctx.lineTo(-14, 10);
                ctx.closePath();
                ctx.fill();
                ctx.restore();

                if (state === 'ready') {
                    ctx.fillStyle = '#38BDF8';
                    ctx.font = 'bold 22px sans-serif';
                    ctx.textAlign = 'center';
                    ctx.fillText('TAP TO FLY', canvas.width / 2, canvas.height / 2);
                } else if (state === 'dead') {
                    ctx.fillStyle = 'rgba(9, 13, 22, 0.85)';
                    ctx.fillRect(0, 0, canvas.width, canvas.height);
                    ctx.fillStyle = '#EF4444';
                    ctx.font = 'bold 26px sans-serif';
                    ctx.textAlign = 'center';
                    ctx.fillText('CRASHED!', canvas.width / 2, canvas.height / 2 - 10);
                    ctx.fillStyle = '#94A3B8';
                    ctx.font = '16px sans-serif';
                    ctx.fillText('Tap to try again', canvas.width / 2, canvas.height / 2 + 25);
                }
            }

            function loop() {
                update();
                draw();
                requestAnimationFrame(loop);
            }

            document.body.addEventListener('touchstart', (e) => {
                if (e.target.closest('.hud')) return;
                e.preventDefault();
                AudioFX.vibrate(14);
                flap();
            }, { passive: false });
            document.body.addEventListener('mousedown', (e) => {
                if (e.target.closest('.hud')) return;
                flap();
            });
            loop();
        </script>
    </body>
    </html>
    """.trimIndent()

    // 7. GAME: KLONDIKE SOLITAIRE
    private fun getSolitaireHtml(): String = """
    <!DOCTYPE html>
    <html>
    <head>
        <title>Klondike Solitaire</title>
        $COMMON_HEAD
        <style>
            .container { display: flex; flex-direction: column; height: calc(100% - 50px); padding: 6px; }
            .top-row { display: flex; justify-content: space-between; margin-bottom: 8px; }
            .card-slot {
                width: 44px; height: 62px;
                border: 1px dashed #334155;
                border-radius: 6px;
                display: flex; align-items: center; justify-content: center;
                background: #131A29;
                font-size: 13px; font-weight: bold;
                position: relative;
            }
            .card {
                width: 44px; height: 62px;
                background: #F8FAFC;
                color: #0F172A;
                border: 1px solid #CBD5E1;
                border-radius: 6px;
                display: flex; flex-direction: column;
                justify-content: space-between;
                padding: 2px 4px;
                font-size: 11px; font-weight: bold;
                box-shadow: 0 1px 3px rgba(0,0,0,0.3);
                cursor: pointer;
            }
            .card.red { color: #DC2626; }
            .card.down { background: #1E293B; border-color: #38BDF8; color: transparent; }
            .tableau { display: flex; justify-content: space-between; flex: 1; }
            .col { display: flex; flex-direction: column; width: 44px; position: relative; }
        </style>
    </head>
    <body>
        <div class="hud">
            <div><span style="color:#94A3B8;">MOVES: </span><span id="moves" class="badge">0</span></div>
            <button class="btn" onclick="deal()">New Deal</button>
        </div>
        <div class="container">
            <div class="top-row">
                <div style="display:flex; gap:6px;">
                    <div id="stock" class="card-slot" onclick="drawStock()">🂠</div>
                    <div id="waste" class="card-slot"></div>
                </div>
                <div style="display:flex; gap:6px;">
                    <div id="f-0" class="card-slot" onclick="foundationClick(0)">♠</div>
                    <div id="f-1" class="card-slot" onclick="foundationClick(1)">♥</div>
                    <div id="f-2" class="card-slot" onclick="foundationClick(2)">♦</div>
                    <div id="f-3" class="card-slot" onclick="foundationClick(3)">♣</div>
                </div>
            </div>
            <div id="tableau" class="tableau"></div>
        </div>

        <script>
            const SUITS = ['♠', '♥', '♦', '♣'];
            const RANKS = ['A', '2', '3', '4', '5', '6', '7', '8', '9', '10', 'J', 'Q', 'K'];
            let stock = [], waste = [], foundations = [[], [], [], []], tableau = [[], [], [], [], [], [], []];
            let moves = 0;

            function deal() {
                moves = 0;
                document.getElementById('moves').innerText = 0;
                let deck = [];
                SUITS.forEach((s, sIdx) => {
                    RANKS.forEach((r, rIdx) => {
                        deck.push({ suit: s, rank: r, value: rIdx + 1, red: s === '♥' || s === '♦', up: false });
                    });
                });
                // Shuffle
                deck.sort(() => Math.random() - 0.5);

                foundations = [[], [], [], []];
                waste = [];
                tableau = [[], [], [], [], [], [], []];

                for (let i = 0; i < 7; i++) {
                    for (let j = i; j < 7; j++) {
                        const c = deck.pop();
                        if (i === j) c.up = true;
                        tableau[j].push(c);
                    }
                }
                stock = deck;
                render();
                AudioFX.play('tap');
            }

            function drawStock() {
                if (stock.length > 0) {
                    const c = stock.pop();
                    c.up = true;
                    waste.push(c);
                } else {
                    stock = waste.reverse().map(c => { c.up = false; return c; });
                    waste = [];
                }
                moves++;
                document.getElementById('moves').innerText = moves;
                AudioFX.play('move');
                render();
            }

            function cardClick(colIdx, cardIdx) {
                const card = tableau[colIdx][cardIdx];
                if (!card.up) {
                    if (cardIdx === tableau[colIdx].length - 1) {
                        card.up = true;
                        moves++;
                        AudioFX.play('tap');
                        render();
                    }
                    return;
                }

                // Try to move to foundation if top card
                if (cardIdx === tableau[colIdx].length - 1) {
                    for (let f = 0; f < 4; f++) {
                        const topF = foundations[f][foundations[f].length - 1];
                        if ((!topF && card.value === 1) || (topF && topF.suit === card.suit && topF.value === card.value - 1)) {
                            foundations[f].push(tableau[colIdx].pop());
                            moves++;
                            AudioFX.play('win');
                            render();
                            return;
                        }
                    }
                }

                // Try to move to another tableau column
                for (let targetCol = 0; targetCol < 7; targetCol++) {
                    if (targetCol === colIdx) continue;
                    const topTarget = tableau[targetCol][tableau[targetCol].length - 1];
                    if ((!topTarget && card.value === 13) || (topTarget && topTarget.red !== card.red && topTarget.value === card.value + 1)) {
                        const movingCards = tableau[colIdx].splice(cardIdx);
                        tableau[targetCol].push(...movingCards);
                        moves++;
                        AudioFX.play('move');
                        render();
                        return;
                    }
                }
            }

            function render() {
                document.getElementById('stock').innerText = stock.length > 0 ? '🂠' : '↺';
                const wasteEl = document.getElementById('waste');
                wasteEl.innerHTML = '';
                if (waste.length > 0) {
                    const c = waste[waste.length - 1];
                    wasteEl.appendChild(createCardEl(c));
                }

                foundations.forEach((f, idx) => {
                    const el = document.getElementById('f-' + idx);
                    el.innerHTML = '';
                    if (f.length > 0) el.appendChild(createCardEl(f[f.length - 1]));
                    else el.innerText = ['♠', '♥', '♦', '♣'][idx];
                });

                const tabEl = document.getElementById('tableau');
                tabEl.innerHTML = '';
                tableau.forEach((col, cIdx) => {
                    const colDiv = document.createElement('div');
                    colDiv.className = 'col';
                    col.forEach((c, idx) => {
                        const cEl = createCardEl(c);
                        cEl.style.position = 'absolute';
                        cEl.style.top = (idx * 16) + 'px';
                        cEl.onclick = () => cardClick(cIdx, idx);
                        colDiv.appendChild(cEl);
                    });
                    tabEl.appendChild(colDiv);
                });
            }

            function createCardEl(c) {
                const div = document.createElement('div');
                div.className = 'card' + (c.red ? ' red' : '') + (!c.up ? ' down' : '');
                if (c.up) {
                    div.innerHTML = '<div>' + c.rank + '</div><div style="font-size:14px; text-align:center;">' + c.suit + '</div>';
                }
                return div;
            }

            deal();
        </script>
    </body>
    </html>
    """.trimIndent()

    // 8. GAME: CONNECT FOUR & TIC-TAC-TOE
    private fun getConnect4Html(): String = """
    <!DOCTYPE html>
    <html>
    <head>
        <title>Connect 4 & Tic-Tac-Toe</title>
        $COMMON_HEAD
        <style>
            .container { display: flex; flex-direction: column; align-items: center; justify-content: center; height: calc(100% - 50px); padding: 8px; }
            .board-c4 {
                display: grid;
                grid-template-columns: repeat(7, 1fr);
                gap: 5px;
                background: #1E3A8A;
                padding: 10px;
                border-radius: 12px;
                width: 100%;
                max-width: 380px;
            }
            .slot {
                background: #090D16;
                aspect-ratio: 1 / 1;
                border-radius: 50%;
                cursor: pointer;
            }
            .slot.p1 { background: #EF4444; box-shadow: 0 0 10px #EF4444; }
            .slot.p2 { background: #FBBF24; box-shadow: 0 0 10px #FBBF24; }
            .tabs { display: flex; gap: 8px; margin-bottom: 8px; }
        </style>
    </head>
    <body>
        <div class="hud">
            <div><span style="color:#94A3B8;">TURN: </span><span id="turn" class="badge" style="color:#EF4444;">Player 1</span></div>
            <button class="btn" onclick="initBoard()">Restart</button>
        </div>
        <div class="container">
            <div id="board" class="board-c4"></div>
            <div style="font-size:12px; color:#64748B; margin-top:10px;">Tap any column to drop a chip</div>
        </div>

        <script>
            const ROWS = 6, COLS = 7;
            let grid = Array.from({length: ROWS}, () => Array(COLS).fill(0));
            let player = 1;
            let gameOver = false;

            function initBoard() {
                grid = Array.from({length: ROWS}, () => Array(COLS).fill(0));
                player = 1;
                gameOver = false;
                document.getElementById('turn').innerText = 'Player 1 (Red)';
                document.getElementById('turn').style.color = '#EF4444';
                render();
                AudioFX.play('tap');
            }

            function drop(col) {
                if (gameOver) return;
                for (let r = ROWS - 1; r >= 0; r--) {
                    if (grid[r][col] === 0) {
                        grid[r][col] = player;
                        AudioFX.play('move');
                        if (checkWin(r, col, player)) {
                            gameOver = true;
                            document.getElementById('turn').innerText = (player === 1 ? 'Player 1 (Red)' : 'Player 2 (Yellow)') + ' WINS!';
                            AudioFX.play('win');
                        } else {
                            player = player === 1 ? 2 : 1;
                            document.getElementById('turn').innerText = player === 1 ? 'Player 1 (Red)' : 'Player 2 (Yellow)';
                            document.getElementById('turn').style.color = player === 1 ? '#EF4444' : '#FBBF24';
                            // Offline AI move if player 2
                            if (player === 2) setTimeout(aiMove, 300);
                        }
                        render();
                        return;
                    }
                }
            }

            function aiMove() {
                if (gameOver) return;
                const validCols = [];
                for (let c = 0; c < COLS; c++) {
                    if (grid[0][c] === 0) validCols.push(c);
                }
                if (validCols.length > 0) {
                    const chosen = validCols[Math.floor(Math.random() * validCols.length)];
                    drop(chosen);
                }
            }

            function checkWin(r, c, p) {
                function count(dr, dc) {
                    let n = 0, nr = r + dr, nc = c + dc;
                    while (nr >= 0 && nr < ROWS && nc >= 0 && nc < COLS && grid[nr][nc] === p) {
                        n++; nr += dr; nc += dc;
                    }
                    return n;
                }
                return (count(0, 1) + count(0, -1) >= 3) ||
                       (count(1, 0) + count(-1, 0) >= 3) ||
                       (count(1, 1) + count(-1, -1) >= 3) ||
                       (count(1, -1) + count(-1, 1) >= 3);
            }

            function render() {
                const bEl = document.getElementById('board');
                bEl.innerHTML = '';
                for (let r = 0; r < ROWS; r++) {
                    for (let c = 0; c < COLS; c++) {
                        const slot = document.createElement('div');
                        slot.className = 'slot' + (grid[r][c] === 1 ? ' p1' : grid[r][c] === 2 ? ' p2' : '');
                        slot.onclick = () => drop(c);
                        bEl.appendChild(slot);
                    }
                }
            }

            initBoard();
        </script>
    </body>
    </html>
    """.trimIndent()

    // 9. GAME: MEMORY MATRIX (CARD FLIP)
    private fun getMemoryHtml(): String = """
    <!DOCTYPE html>
    <html>
    <head>
        <title>Memory Matrix</title>
        $COMMON_HEAD
        <style>
            .container { display: flex; flex-direction: column; align-items: center; justify-content: center; height: calc(100% - 50px); padding: 8px; }
            .grid {
                display: grid;
                grid-template-columns: repeat(4, 1fr);
                gap: 8px;
                width: 100%;
                max-width: 360px;
                aspect-ratio: 4 / 4;
            }
            .card {
                background: #1E293B;
                border: 2px solid #334155;
                border-radius: 8px;
                display: flex; align-items: center; justify-content: center;
                font-size: 26px;
                cursor: pointer;
                transition: transform 0.2s ease;
            }
            .card.flipped { background: #0284C7; border-color: #38BDF8; transform: rotateY(180deg); }
            .card.matched { background: #059669; border-color: #34D399; }
        </style>
    </head>
    <body>
        <div class="hud">
            <div><span style="color:#94A3B8;">MOVES: </span><span id="moves" class="badge">0</span></div>
            <div><span style="color:#94A3B8;">MATCHES: </span><span id="matches" class="badge">0 / 8</span></div>
            <button class="btn" onclick="initGame()">Restart</button>
        </div>
        <div class="container">
            <div id="grid" class="grid"></div>
        </div>

        <script>
            const ICONS = ['🚀', '⚡', '💎', '🔥', '👾', '🕹️', '🛡️', '🌟'];
            let cards = [];
            let flipped = [];
            let moves = 0, matches = 0;
            let locked = false;

            function initGame() {
                const deck = [...ICONS, ...ICONS].sort(() => Math.random() - 0.5);
                cards = deck.map(icon => ({ icon, flipped: false, matched: false }));
                flipped = []; moves = 0; matches = 0; locked = false;
                document.getElementById('moves').innerText = 0;
                document.getElementById('matches').innerText = '0 / 8';
                render();
                AudioFX.play('tap');
            }

            function flipCard(idx) {
                if (locked || cards[idx].flipped || cards[idx].matched) return;
                cards[idx].flipped = true;
                flipped.push(idx);
                AudioFX.play('move');
                render();

                if (flipped.length === 2) {
                    moves++;
                    document.getElementById('moves').innerText = moves;
                    const [c1, c2] = flipped;
                    if (cards[c1].icon === cards[c2].icon) {
                        cards[c1].matched = true;
                        cards[c2].matched = true;
                        matches++;
                        document.getElementById('matches').innerText = matches + ' / 8';
                        AudioFX.play('score');
                        flipped = [];
                        if (matches === 8) AudioFX.play('win');
                    } else {
                        locked = true;
                        setTimeout(() => {
                            cards[c1].flipped = false;
                            cards[c2].flipped = false;
                            flipped = [];
                            locked = false;
                            render();
                        }, 750);
                    }
                }
            }

            function render() {
                const gEl = document.getElementById('grid');
                gEl.innerHTML = '';
                cards.forEach((c, idx) => {
                    const div = document.createElement('div');
                    div.className = 'card' + (c.flipped || c.matched ? ' flipped' : '') + (c.matched ? ' matched' : '');
                    div.innerText = (c.flipped || c.matched) ? c.icon : '';
                    div.onclick = () => flipCard(idx);
                    gEl.appendChild(div);
                });
            }

            initGame();
        </script>
    </body>
    </html>
    """.trimIndent()

    // 10. GAME: NEON BREAKOUT
    private fun getBreakoutHtml(): String = """
    <!DOCTYPE html>
    <html>
    <head>
        <title>Neon Breakout</title>
        $COMMON_HEAD
        <style>
            .container { display: flex; flex-direction: column; align-items: center; justify-content: center; height: calc(100% - 50px); padding: 4px; }
            #breakout-canvas {
                background: #090D16;
                border: 2px solid #1E293B;
                border-radius: 12px;
                max-width: 95vw;
                height: 60vh;
                touch-action: none;
            }
            .dpad { display: flex; gap: 14px; margin-top: 12px; width: 100%; max-width: 360px; justify-content: center; }
            .pbtn { width: 48%; height: 52px; font-size: 18px; border-radius: 12px; }
        </style>
    </head>
    <body>
        <div class="hud">
            <div><span style="color:#94A3B8;">SCORE: </span><span id="score" class="badge">0</span></div>
            <div><span style="color:#94A3B8;">LIVES: </span><span id="lives" class="badge">3</span></div>
            <button class="btn" onclick="initGame()">Restart</button>
        </div>
        <div class="container">
            <canvas id="breakout-canvas" width="360" height="480"></canvas>
            <div class="dpad">
                <button id="p-left" class="btn pbtn">◀ Left</button>
                <button id="p-right" class="btn pbtn">Right ▶</button>
            </div>
        </div>

        <script>
            const canvas = document.getElementById('breakout-canvas');
            const ctx = canvas.getContext('2d');

            let paddleW = 75, paddleH = 10;
            let paddleX = (canvas.width - paddleW) / 2;
            let ballX = canvas.width / 2, ballY = canvas.height - 35;
            let dx = 3, dy = -3;
            let ballR = 6;
            let score = 0, lives = 3;
            let bricks = [];
            const BRICK_ROWS = 5, BRICK_COLS = 6, BRICK_W = 50, BRICK_H = 16, BRICK_PADDING = 7, BRICK_TOP = 40;
            const COLORS = ['#EF4444', '#F97316', '#EAB308', '#22C55E', '#06B6D4'];

            function initGame() {
                score = 0; lives = 3;
                paddleX = (canvas.width - paddleW) / 2;
                resetBall();
                bricks = [];
                for (let r = 0; r < BRICK_ROWS; r++) {
                    bricks[r] = [];
                    for (let c = 0; c < BRICK_COLS; c++) {
                        bricks[r][c] = { x: 0, y: 0, status: 1, color: COLORS[r] };
                    }
                }
                document.getElementById('score').innerText = 0;
                document.getElementById('lives').innerText = 3;
                AudioFX.play('tap');
            }

            function resetBall() {
                ballX = canvas.width / 2; ballY = canvas.height - 35;
                dx = 3; dy = -3;
            }

            function paddleLeft() { paddleX = Math.max(0, paddleX - 25); }
            function paddleRight() { paddleX = Math.min(canvas.width - paddleW, paddleX + 25); }

            // Drag paddle on touch (both touchstart and touchmove)
            function setPaddleFromTouch(clientX) {
                const rect = canvas.getBoundingClientRect();
                const touchX = clientX - rect.left;
                paddleX = Math.max(0, Math.min(canvas.width - paddleW, (touchX / rect.width) * canvas.width - paddleW / 2));
            }
            canvas.addEventListener('touchstart', e => {
                setPaddleFromTouch(e.touches[0].clientX);
            }, { passive: true });
            canvas.addEventListener('touchmove', e => {
                setPaddleFromTouch(e.touches[0].clientX);
            }, { passive: true });

            bindTouchRepeat(document.getElementById('p-left'), () => paddleLeft(), 40, 160);
            bindTouchRepeat(document.getElementById('p-right'), () => paddleRight(), 40, 160);

            function update() {
                ballX += dx;
                ballY += dy;

                if (ballX + dx > canvas.width - ballR || ballX + dx < ballR) {
                    dx = -dx; AudioFX.play('move');
                }
                if (ballY + dy < ballR) {
                    dy = -dy; AudioFX.play('move');
                } else if (ballY + dy > canvas.height - paddleH - 15) {
                    if (ballX > paddleX && ballX < paddleX + paddleW) {
                        dy = -Math.abs(dy);
                        AudioFX.play('tap');
                    } else if (ballY > canvas.height) {
                        lives--;
                        document.getElementById('lives').innerText = lives;
                        AudioFX.play('boom');
                        if (lives <= 0) initGame();
                        else resetBall();
                    }
                }

                // Brick collisions
                for (let r = 0; r < BRICK_ROWS; r++) {
                    for (let c = 0; c < BRICK_COLS; c++) {
                        const b = bricks[r][c];
                        if (b.status === 1) {
                            const bx = c * (BRICK_W + BRICK_PADDING) + 12;
                            const by = r * (BRICK_H + BRICK_PADDING) + BRICK_TOP;
                            b.x = bx; b.y = by;
                            if (ballX > bx && ballX < bx + BRICK_W && ballY > by && ballY < by + BRICK_H) {
                                dy = -dy;
                                b.status = 0;
                                score += 20;
                                document.getElementById('score').innerText = score;
                                AudioFX.play('score');
                            }
                        }
                    }
                }
            }

            function draw() {
                ctx.fillStyle = '#090D16';
                ctx.fillRect(0, 0, canvas.width, canvas.height);

                // Bricks
                for (let r = 0; r < BRICK_ROWS; r++) {
                    for (let c = 0; c < BRICK_COLS; c++) {
                        const b = bricks[r][c];
                        if (b.status === 1) {
                            ctx.fillStyle = b.color;
                            ctx.fillRect(b.x, b.y, BRICK_W, BRICK_H);
                        }
                    }
                }

                // Paddle
                ctx.fillStyle = '#38BDF8';
                ctx.shadowColor = '#38BDF8';
                ctx.shadowBlur = 8;
                ctx.fillRect(paddleX, canvas.height - paddleH - 12, paddleW, paddleH);
                ctx.shadowBlur = 0;

                // Ball
                ctx.fillStyle = '#F8FAFC';
                ctx.beginPath();
                ctx.arc(ballX, ballY, ballR, 0, Math.PI * 2);
                ctx.fill();
            }

            function loop() {
                update();
                draw();
                requestAnimationFrame(loop);
            }

            initGame();
            loop();
        </script>
    </body>
    </html>
    """.trimIndent()

    // 11. GAME: CYBER BLADE SLICER (Fruit Ninja Style)
    private fun getSlicerHtml(): String = """
    <!DOCTYPE html>
    <html>
    <head>
        <title>Cyber Blade Slicer</title>
        $COMMON_HEAD
        <style>
            .container { display: flex; flex-direction: column; align-items: center; justify-content: center; height: calc(100% - 50px); padding: 4px; }
            #slicer-canvas {
                background: #070A12;
                border: 2px solid #1E293B;
                border-radius: 12px;
                box-shadow: 0 0 20px rgba(56, 189, 248, 0.2);
                max-width: 96vw;
                height: 82vh;
                touch-action: none;
            }
        </style>
    </head>
    <body>
        <div class="hud">
            <div><span style="color:#94A3B8;">SCORE: </span><span id="score" class="badge">0</span></div>
            <div><span style="color:#94A3B8;">BEST: </span><span id="best" class="badge">0</span></div>
            <div><span id="lives" style="font-size:16px;">❤️❤️❤️</span></div>
            <button class="btn" onclick="initGame()">Restart</button>
        </div>
        <div class="container">
            <canvas id="slicer-canvas" width="380" height="580"></canvas>
        </div>

        <script>
            const canvas = document.getElementById('slicer-canvas');
            const ctx = canvas.getContext('2d');

            let score = 0, lives = 3, gameOver = false;
            let best = parseInt(SafeStorage.getItem('slicer_best') || '0', 10);
            document.getElementById('best').innerText = best;

            let targets = [];
            let particles = [];
            let floatingTexts = [];
            let bladeTrail = [];
            let sliceCountInStroke = 0;
            let isSlicing = false;
            let lastSpawnTime = 0;

            const TYPES = {
                MELON: { radius: 26, color: '#22C55E', points: 1, label: '🍈' },
                BERRY: { radius: 20, color: '#EC4899', points: 2, label: '🍓' },
                STAR: { radius: 18, color: '#FBBF24', points: 3, label: '⭐' },
                BOMB: { radius: 24, color: '#EF4444', points: 0, label: '💣' }
            };

            function initGame() {
                score = 0; lives = 3; gameOver = false;
                targets = []; particles = []; floatingTexts = []; bladeTrail = [];
                document.getElementById('score').innerText = 0;
                document.getElementById('lives').innerText = '❤️❤️❤️';
                AudioFX.play('tap');
            }

            function spawnWave() {
                if (gameOver) return;
                const count = Math.floor(Math.random() * 3) + 1;
                for (let i = 0; i < count; i++) {
                    const isBomb = Math.random() < 0.22;
                    let typeKey = 'MELON';
                    if (isBomb) typeKey = 'BOMB';
                    else {
                        const r = Math.random();
                        typeKey = r < 0.5 ? 'MELON' : (r < 0.8 ? 'BERRY' : 'STAR');
                    }
                    const t = TYPES[typeKey];
                    const x = 50 + Math.random() * (canvas.width - 100);
                    const vx = (canvas.width / 2 - x) * 0.015 + (Math.random() - 0.5) * 3;
                    const vy = -12 - Math.random() * 4;
                    targets.push({
                        type: typeKey,
                        radius: t.radius,
                        color: t.color,
                        label: t.label,
                        points: t.points,
                        x, y: canvas.height + 20,
                        vx, vy,
                        rot: 0, vRot: (Math.random() - 0.5) * 0.15,
                        sliced: false
                    });
                }
            }

            function addParticles(x, y, color) {
                for (let i = 0; i < 14; i++) {
                    const angle = Math.random() * Math.PI * 2;
                    const speed = 2 + Math.random() * 5;
                    particles.push({
                        x, y,
                        vx: Math.cos(angle) * speed,
                        vy: Math.sin(angle) * speed,
                        radius: 2 + Math.random() * 3,
                        color,
                        alpha: 1.0,
                        decay: 0.03 + Math.random() * 0.03
                    });
                }
            }

            function checkSlice(p1, p2) {
                if (gameOver) return;
                targets.forEach(t => {
                    if (t.sliced) return;
                    // Distance from target to line segment p1-p2
                    const l2 = (p2.x - p1.x)**2 + (p2.y - p1.y)**2;
                    let d = 0;
                    if (l2 === 0) {
                        d = Math.hypot(t.x - p1.x, t.y - p1.y);
                    } else {
                        let u = ((t.x - p1.x)*(p2.x - p1.x) + (t.y - p1.y)*(p2.y - p1.y)) / l2;
                        u = Math.max(0, Math.min(1, u));
                        const projX = p1.x + u * (p2.x - p1.x);
                        const projY = p1.y + u * (p2.y - p1.y);
                        d = Math.hypot(t.x - projX, t.y - projY);
                    }

                    if (d < t.radius + 10) {
                        t.sliced = true;
                        addParticles(t.x, t.y, t.color);
                        if (t.type === 'BOMB') {
                            AudioFX.play('over');
                            AudioFX.vibrate(50);
                            lives = 0;
                            gameOver = true;
                            document.getElementById('lives').innerText = '💥💥💥';
                        } else {
                            sliceCountInStroke++;
                            score += t.points;
                            AudioFX.play('merge');
                            AudioFX.vibrate(15);
                            if (score > best) {
                                best = score;
                                SafeStorage.setItem('slicer_best', best);
                                document.getElementById('best').innerText = best;
                            }
                            document.getElementById('score').innerText = score;
                            if (sliceCountInStroke >= 3) {
                                const comboBonus = sliceCountInStroke * 5;
                                score += comboBonus;
                                floatingTexts.push({
                                    text: sliceCountInStroke + 'x COMBO! +' + comboBonus,
                                    x: t.x, y: t.y, alpha: 1.0, color: '#38BDF8'
                                });
                            }
                        }
                    }
                });
            }

            function getTouchPos(e) {
                const rect = canvas.getBoundingClientRect();
                const touch = e.touches ? e.touches[0] : e;
                return {
                    x: (touch.clientX - rect.left) * (canvas.width / rect.width),
                    y: (touch.clientY - rect.top) * (canvas.height / rect.height),
                    time: Date.now()
                };
            }

            canvas.addEventListener('touchstart', e => {
                if (gameOver) { initGame(); return; }
                isSlicing = true;
                sliceCountInStroke = 0;
                bladeTrail = [getTouchPos(e)];
            }, { passive: true });

            canvas.addEventListener('touchmove', e => {
                if (!isSlicing) return;
                const pos = getTouchPos(e);
                bladeTrail.push(pos);
                if (bladeTrail.length > 1) {
                    checkSlice(bladeTrail[bladeTrail.length - 2], pos);
                }
            }, { passive: true });

            canvas.addEventListener('touchend', () => {
                isSlicing = false;
                sliceCountInStroke = 0;
            });

            function update() {
                const now = Date.now();
                if (now - lastSpawnTime > 1800 && targets.length < 5) {
                    spawnWave();
                    lastSpawnTime = now;
                }

                // Update targets
                targets.forEach(t => {
                    t.x += t.vx;
                    t.y += t.vy;
                    t.vy += 0.32; // gravity
                    t.rot += t.vRot;

                    if (!t.sliced && t.y > canvas.height + 40 && t.vy > 0) {
                        t.sliced = true; // escaped
                        if (t.type !== 'BOMB' && !gameOver) {
                            lives--;
                            AudioFX.play('tap');
                            AudioFX.vibrate(25);
                            const hearts = ['','❤️','❤️❤️','❤️❤️❤️'];
                            document.getElementById('lives').innerText = hearts[Math.max(0, lives)] || '💀';
                            if (lives <= 0) {
                                gameOver = true;
                                AudioFX.play('over');
                            }
                        }
                    }
                });
                targets = targets.filter(t => t.y < canvas.height + 60 && (!t.sliced || t.y < canvas.height));

                // Particles
                particles.forEach(p => {
                    p.x += p.vx; p.y += p.vy; p.vy += 0.15;
                    p.alpha -= p.decay;
                });
                particles = particles.filter(p => p.alpha > 0);

                // Blade trail decay
                bladeTrail = bladeTrail.filter(pt => now - pt.time < 120);

                // Floating texts
                floatingTexts.forEach(ft => { ft.y -= 1; ft.alpha -= 0.02; });
                floatingTexts = floatingTexts.filter(ft => ft.alpha > 0);
            }

            function draw() {
                ctx.fillStyle = '#070A12';
                ctx.fillRect(0, 0, canvas.width, canvas.height);

                // Targets
                targets.forEach(t => {
                    if (t.sliced) return;
                    ctx.save();
                    ctx.translate(t.x, t.y);
                    ctx.rotate(t.rot);
                    ctx.shadowColor = t.color;
                    ctx.shadowBlur = 12;
                    ctx.fillStyle = t.color;
                    ctx.beginPath();
                    ctx.arc(0, 0, t.radius, 0, Math.PI * 2);
                    ctx.fill();

                    // Inner icon
                    ctx.shadowBlur = 0;
                    ctx.font = (t.radius * 1.1) + 'px sans-serif';
                    ctx.textAlign = 'center';
                    ctx.textBaseline = 'middle';
                    ctx.fillText(t.label, 0, 2);
                    ctx.restore();
                });

                // Particles
                particles.forEach(p => {
                    ctx.save();
                    ctx.globalAlpha = p.alpha;
                    ctx.fillStyle = p.color;
                    ctx.shadowColor = p.color;
                    ctx.shadowBlur = 6;
                    ctx.beginPath();
                    ctx.arc(p.x, p.y, p.radius, 0, Math.PI * 2);
                    ctx.fill();
                    ctx.restore();
                });

                // Neon Blade Trail
                if (bladeTrail.length > 1) {
                    ctx.save();
                    ctx.strokeStyle = '#38BDF8';
                    ctx.lineWidth = 6;
                    ctx.lineCap = 'round';
                    ctx.lineJoin = 'round';
                    ctx.shadowColor = '#00F0FF';
                    ctx.shadowBlur = 14;
                    ctx.beginPath();
                    ctx.moveTo(bladeTrail[0].x, bladeTrail[0].y);
                    for (let i = 1; i < bladeTrail.length; i++) {
                        ctx.lineTo(bladeTrail[i].x, bladeTrail[i].y);
                    }
                    ctx.stroke();

                    // Inner bright core
                    ctx.strokeStyle = '#FFFFFF';
                    ctx.lineWidth = 2;
                    ctx.stroke();
                    ctx.restore();
                }

                // Floating combo text
                floatingTexts.forEach(ft => {
                    ctx.save();
                    ctx.globalAlpha = ft.alpha;
                    ctx.fillStyle = ft.color;
                    ctx.font = 'bold 20px sans-serif';
                    ctx.textAlign = 'center';
                    ctx.fillText(ft.text, ft.x, ft.y);
                    ctx.restore();
                });

                if (gameOver) {
                    ctx.fillStyle = 'rgba(7, 10, 18, 0.88)';
                    ctx.fillRect(0, 0, canvas.width, canvas.height);
                    ctx.fillStyle = '#EF4444';
                    ctx.font = 'bold 30px sans-serif';
                    ctx.textAlign = 'center';
                    ctx.fillText('GAME OVER', canvas.width / 2, canvas.height / 2 - 20);
                    ctx.fillStyle = '#38BDF8';
                    ctx.font = 'bold 22px sans-serif';
                    ctx.fillText('Score: ' + score, canvas.width / 2, canvas.height / 2 + 20);
                    ctx.fillStyle = '#94A3B8';
                    ctx.font = '15px sans-serif';
                    ctx.fillText('Tap Restart to slice again', canvas.width / 2, canvas.height / 2 + 55);
                }
            }

            function loop() {
                update();
                draw();
                requestAnimationFrame(loop);
            }

            initGame();
            loop();
        </script>
    </body>
    </html>
    """.trimIndent()

    // 12. GAME: PIXEL CROSSING (Crossy Road Style)
    private fun getCrossingHtml(): String = """
    <!DOCTYPE html>
    <html>
    <head>
        <title>Pixel Crossing</title>
        $COMMON_HEAD
        <style>
            .container {
                display: flex;
                flex-direction: column;
                align-items: center;
                justify-content: center;
                height: calc(100% - 50px);
                padding: 4px;
                position: relative;
            }
            #crossing-canvas {
                background: #090D16;
                border: 2px solid #1E293B;
                border-radius: 12px;
                box-shadow: 0 0 20px rgba(34, 197, 94, 0.2);
                max-width: 96vw;
                height: 72vh;
                touch-action: none;
            }
            .crossing-dpad {
                display: grid;
                grid-template-columns: repeat(3, 64px);
                gap: 6px;
                margin-top: 8px;
            }
            .cdbtn {
                height: 48px;
                font-size: 22px;
                border-radius: 12px;
                background: #1E293B;
                border: 1px solid #334155;
                color: #38BDF8;
                font-weight: bold;
                display: flex;
                align-items: center;
                justify-content: center;
            }
            .cdbtn:active {
                background: #38BDF8;
                color: #0F172A;
            }
        </style>
    </head>
    <body>
        <div class="hud">
            <div><span style="color:#94A3B8;">DISTANCE: </span><span id="score" class="badge">0</span></div>
            <div><span style="color:#94A3B8;">BEST: </span><span id="best" class="badge">0</span></div>
            <button class="btn" onclick="initGame()">Restart</button>
        </div>
        <div class="container">
            <canvas id="crossing-canvas" width="380" height="520"></canvas>
            <div class="crossing-dpad">
                <div></div>
                <button class="btn cdbtn" ontouchstart="hop(0, 1); event.preventDefault();" onclick="hop(0, 1)">▲</button>
                <div></div>
                <button class="btn cdbtn" ontouchstart="hop(-1, 0); event.preventDefault();" onclick="hop(-1, 0)">◀</button>
                <button class="btn cdbtn" ontouchstart="hop(0, -1); event.preventDefault();" onclick="hop(0, -1)">▼</button>
                <button class="btn cdbtn" ontouchstart="hop(1, 0); event.preventDefault();" onclick="hop(1, 0)">▶</button>
            </div>
        </div>

        <script>
            const canvas = document.getElementById('crossing-canvas');
            const ctx = canvas.getContext('2d');

            const LANE_H = 38;
            const PLAYER_SIZE = 26;

            let player = { x: 190 - PLAYER_SIZE / 2, row: 0, hopBounce: 0 };
            let maxRow = 0;
            let cameraRow = 0;
            let gameOver = false;
            let deathReason = 'CRASH';
            let idleTimer = Date.now();
            let lanes = [];
            let consecutiveHazards = 0;

            let best = parseInt(SafeStorage.getItem('crossing_best') || '0', 10);
            document.getElementById('best').innerText = best;

            function generateLane(row) {
                // First 4 rows are always peaceful safe grass
                if (row <= 3) {
                    consecutiveHazards = 0;
                    return { type: 'GRASS', row, obstacles: [] };
                }

                // Never more than 3 consecutive hazard rows without a resting grass strip
                if (consecutiveHazards >= 3) {
                    consecutiveHazards = 0;
                    return { type: 'GRASS', row, obstacles: [] };
                }

                const rand = Math.random();
                let type = 'GRASS';
                if (rand < 0.25) {
                    consecutiveHazards = 0;
                    type = 'GRASS';
                } else if (rand < 0.55) {
                    consecutiveHazards++;
                    type = 'ROAD';
                } else if (rand < 0.72) {
                    consecutiveHazards++;
                    type = 'HIGHWAY';
                } else if (rand < 0.88) {
                    consecutiveHazards++;
                    type = 'RIVER';
                } else {
                    consecutiveHazards++;
                    type = 'RAIL';
                }

                const dir = Math.random() < 0.5 ? 1 : -1;
                let obstacles = [];

                if (type === 'ROAD') {
                    const speed = (2.0 + Math.random() * 0.8) * dir;
                    const count = Math.random() < 0.5 ? 2 : 3;
                    const spacing = canvas.width / count;
                    for (let i = 0; i < count; i++) {
                        obstacles.push({
                            x: i * spacing + Math.random() * 15,
                            width: 50,
                            speed,
                            color: '#EF4444'
                        });
                    }
                } else if (type === 'HIGHWAY') {
                    const speed = (3.2 + Math.random() * 0.8) * dir;
                    const count = 2;
                    const spacing = canvas.width / count;
                    for (let i = 0; i < count; i++) {
                        obstacles.push({
                            x: i * spacing,
                            width: 66,
                            speed,
                            color: '#F59E0B'
                        });
                    }
                } else if (type === 'RIVER') {
                    const speed = (1.2 + Math.random() * 0.5) * dir;
                    const count = 2;
                    const spacing = canvas.width / count;
                    for (let i = 0; i < count; i++) {
                        obstacles.push({
                            x: i * spacing + (Math.random() - 0.5) * 20,
                            width: 125, // Wide, stable floating log
                            speed,
                            color: '#78350F'
                        });
                    }
                } else if (type === 'RAIL') {
                    // Train track with periodic high-speed bullet train
                    obstacles.push({
                        type: 'TRAIN',
                        x: -400,
                        width: 340,
                        speed: 18 * dir,
                        dir,
                        active: false,
                        warningTicks: 0,
                        cooldown: 180 + Math.floor(Math.random() * 180),
                        color: '#A855F7'
                    });
                }

                return { type, row, obstacles };
            }

            function initGame() {
                player = { x: 190 - PLAYER_SIZE / 2, row: 0, hopBounce: 0 };
                maxRow = 0;
                cameraRow = 0;
                gameOver = false;
                deathReason = 'CRASH';
                idleTimer = Date.now();
                consecutiveHazards = 0;
                lanes = [];
                for (let r = 0; r < 30; r++) lanes.push(generateLane(r));
                document.getElementById('score').innerText = 0;
                AudioFX.play('tap');
            }

            function hop(dx, dy) {
                if (gameOver) { initGame(); return; }
                AudioFX.init();

                if (dy !== 0) {
                    player.row = Math.max(0, player.row + dy);
                    // Retain continuous horizontal position when hopping vertically without forced grid snaps
                    player.x = Math.max(6, Math.min(canvas.width - PLAYER_SIZE - 6, player.x));

                    if (player.row > maxRow) {
                        maxRow = player.row;
                        document.getElementById('score').innerText = maxRow;
                        if (maxRow > best) {
                            best = maxRow;
                            SafeStorage.setItem('crossing_best', best);
                            document.getElementById('best').innerText = best;
                        }
                        while (lanes.length <= maxRow + 25) {
                            lanes.push(generateLane(lanes.length));
                        }
                    }
                }

                if (dx !== 0) {
                    player.x = Math.max(6, Math.min(canvas.width - PLAYER_SIZE - 6, player.x + dx * 34));
                }

                player.hopBounce = 1.0;
                idleTimer = Date.now();
                AudioFX.play('move');
                AudioFX.vibrate(12);
            }

            // Screen Touch & Swipe Controls across entire screen
            let touchStartX = 0, touchStartY = 0, touchStartTime = 0;
            document.addEventListener('touchstart', e => {
                if (e.target.closest('.hud') || e.target.closest('.crossing-dpad')) return;
                if (gameOver) { initGame(); return; }
                touchStartX = e.touches[0].clientX;
                touchStartY = e.touches[0].clientY;
                touchStartTime = Date.now();
            }, { passive: true });

            document.addEventListener('touchend', e => {
                if (e.target.closest('.hud') || e.target.closest('.crossing-dpad')) return;
                if (gameOver) return;
                const dx = e.changedTouches[0].clientX - touchStartX;
                const dy = e.changedTouches[0].clientY - touchStartY;
                const dt = Date.now() - touchStartTime;
                const absDx = Math.abs(dx);
                const absDy = Math.abs(dy);

                // Forgiving tap detection: small movement within 360ms = Hop Forward!
                if (absDx < 24 && absDy < 24 && dt < 360) {
                    hop(0, 1);
                } else if (absDx > absDy && absDx > 24) {
                    hop(dx > 0 ? 1 : -1, 0); // Swipe Left or Right
                } else if (absDy > absDx && absDy > 24) {
                    hop(0, dy < 0 ? 1 : -1); // Swipe Up (dy<0) = Forward, Swipe Down = Back
                }
            }, { passive: true });

            // Keyboard arrows
            window.addEventListener('keydown', e => {
                if (e.key === 'ArrowUp') hop(0, 1);
                else if (e.key === 'ArrowDown') hop(0, -1);
                else if (e.key === 'ArrowLeft') hop(-1, 0);
                else if (e.key === 'ArrowRight') hop(1, 0);
            });

            function update() {
                if (gameOver) return;

                // Smooth camera following player
                const targetCam = Math.max(0, player.row - 3);
                cameraRow += (targetCam - cameraRow) * 0.12;

                if (player.hopBounce > 0) player.hopBounce = Math.max(0, player.hopBounce - 0.12);

                // Update obstacles across all active lanes
                lanes.forEach(lane => {
                    lane.obstacles.forEach(obs => {
                        if (obs.type === 'TRAIN') {
                            obs.cooldown--;
                            if (obs.cooldown < 60 && obs.cooldown > 0) {
                                obs.warningTicks++;
                                if (obs.cooldown % 20 === 0) AudioFX.play('tap');
                            }
                            if (obs.cooldown <= 0 && !obs.active) {
                                obs.active = true;
                                obs.x = obs.dir > 0 ? -obs.width - 20 : canvas.width + 20;
                            }
                            if (obs.active) {
                                obs.x += obs.speed;
                                if ((obs.dir > 0 && obs.x > canvas.width + 60) || (obs.dir < 0 && obs.x < -obs.width - 60)) {
                                    obs.active = false;
                                    obs.cooldown = 200 + Math.floor(Math.random() * 200);
                                    obs.warningTicks = 0;
                                }
                            }
                        } else {
                            obs.x += obs.speed;
                            if (obs.speed > 0 && obs.x > canvas.width + 60) obs.x = -obs.width - 20;
                            if (obs.speed < 0 && obs.x < -obs.width - 60) obs.x = canvas.width + 20;
                        }
                    });
                });

                // Accurate Collision Detection
                const currentLane = lanes[player.row];
                if (currentLane) {
                    const pLeft = player.x + 3;
                    const pRight = player.x + PLAYER_SIZE - 3;
                    const pCenter = player.x + PLAYER_SIZE / 2;

                    if (currentLane.type === 'ROAD' || currentLane.type === 'HIGHWAY') {
                        currentLane.obstacles.forEach(obs => {
                            const oLeft = obs.x + 3;
                            const oRight = obs.x + obs.width - 3;
                            // AABB 1D overlap
                            if (pLeft < oRight && pRight > oLeft) {
                                gameOver = true;
                                deathReason = 'CAR';
                                AudioFX.play('over');
                                AudioFX.vibrate(45);
                            }
                        });
                    } else if (currentLane.type === 'RAIL') {
                        const train = currentLane.obstacles[0];
                        if (train && train.active) {
                            const tLeft = train.x;
                            const tRight = train.x + train.width;
                            if (pLeft < tRight && pRight > tLeft) {
                                gameOver = true;
                                deathReason = 'TRAIN';
                                AudioFX.play('over');
                                AudioFX.vibrate(50);
                            }
                        }
                    } else if (currentLane.type === 'RIVER') {
                        let onLog = false;
                        currentLane.obstacles.forEach(log => {
                            // Generous 10px landing margin on floating raft tips
                            if (pCenter >= log.x - 10 && pCenter <= log.x + log.width + 10) {
                                onLog = true;
                                player.x += log.speed;
                            }
                        });

                        // Fell in water or drifted off screen
                        if (!onLog || player.x < 0 || player.x > canvas.width - PLAYER_SIZE) {
                            gameOver = true;
                            deathReason = 'WATER';
                            AudioFX.play('boom');
                            AudioFX.vibrate(45);
                        }
                    }
                }

                // Generous 20s idle timeout
                if (Date.now() - idleTimer > 20000) {
                    gameOver = true;
                    deathReason = 'IDLE';
                    AudioFX.play('over');
                }
            }

            function draw() {
                ctx.fillStyle = '#090D16';
                ctx.fillRect(0, 0, canvas.width, canvas.height);

                const baseScreenY = canvas.height - 110;

                // Draw lanes
                lanes.forEach(lane => {
                    const sy = baseScreenY - (lane.row - cameraRow) * LANE_H;
                    if (sy < -LANE_H || sy > canvas.height + LANE_H) return;

                    if (lane.type === 'GRASS') {
                        ctx.fillStyle = '#0F291E';
                        ctx.fillRect(0, sy, canvas.width, LANE_H);
                        // Subtle grass tufts
                        ctx.fillStyle = '#164E33';
                        ctx.fillRect(30, sy + 6, 4, 8);
                        ctx.fillRect(160, sy + 18, 4, 8);
                        ctx.fillRect(310, sy + 10, 4, 8);
                    } else if (lane.type === 'ROAD' || lane.type === 'HIGHWAY') {
                        ctx.fillStyle = lane.type === 'ROAD' ? '#181E29' : '#111827';
                        ctx.fillRect(0, sy, canvas.width, LANE_H);
                        // Dashed road stripes
                        ctx.fillStyle = 'rgba(255,255,255,0.2)';
                        for (let x = 10; x < canvas.width; x += 36) {
                            ctx.fillRect(x, sy + LANE_H / 2 - 1, 16, 2);
                        }
                    } else if (lane.type === 'RIVER') {
                        ctx.fillStyle = '#0C2847';
                        ctx.fillRect(0, sy, canvas.width, LANE_H);
                        // Gentle water ripples
                        ctx.fillStyle = '#1E3A5F';
                        ctx.fillRect(40, sy + 12, 28, 2);
                        ctx.fillRect(200, sy + 24, 34, 2);
                    } else if (lane.type === 'RAIL') {
                        ctx.fillStyle = '#1E1B4B';
                        ctx.fillRect(0, sy, canvas.width, LANE_H);
                        // Sleepers and rails
                        ctx.fillStyle = '#374151';
                        for (let x = 8; x < canvas.width; x += 18) {
                            ctx.fillRect(x, sy + 4, 6, LANE_H - 8);
                        }
                        ctx.fillStyle = '#6B7280';
                        ctx.fillRect(0, sy + 8, canvas.width, 3);
                        ctx.fillRect(0, sy + LANE_H - 11, canvas.width, 3);

                        // Signal light
                        const train = lane.obstacles[0];
                        if (train && train.cooldown < 60) {
                            ctx.fillStyle = (Math.floor(train.warningTicks / 8) % 2 === 0) ? '#EF4444' : '#7F1D1D';
                            ctx.shadowColor = '#EF4444';
                            ctx.shadowBlur = 10;
                            ctx.beginPath();
                            ctx.arc(canvas.width - 20, sy + LANE_H / 2, 7, 0, Math.PI * 2);
                            ctx.fill();
                            ctx.shadowBlur = 0;
                        }
                    }

                    // Draw Obstacles (Cars, Trucks, Logs, Train)
                    lane.obstacles.forEach(obs => {
                        if (obs.type === 'TRAIN') {
                            if (obs.active) {
                                ctx.save();
                                ctx.fillStyle = '#A855F7';
                                ctx.shadowColor = '#C084FC';
                                ctx.shadowBlur = 12;
                                ctx.fillRect(obs.x, sy + 4, obs.width, LANE_H - 8);
                                // Headlight
                                ctx.fillStyle = '#FEF08A';
                                const hx = obs.dir > 0 ? obs.x + obs.width - 6 : obs.x + 2;
                                ctx.fillRect(hx, sy + 6, 4, LANE_H - 12);
                                ctx.restore();
                            }
                        } else {
                            ctx.save();
                            ctx.fillStyle = obs.color;
                            ctx.shadowColor = obs.color;
                            ctx.shadowBlur = lane.type === 'RIVER' ? 0 : 6;
                            ctx.fillRect(obs.x, sy + 5, obs.width, LANE_H - 10);
                            // Detail trim (log bark or windshield)
                            if (lane.type === 'RIVER') {
                                ctx.fillStyle = '#542609';
                                ctx.fillRect(obs.x + 8, sy + 9, obs.width - 16, LANE_H - 18);
                            } else {
                                ctx.fillStyle = 'rgba(255,255,255,0.25)';
                                ctx.fillRect(obs.x + 6, sy + 8, obs.width - 12, 4);
                            }
                            ctx.restore();
                        }
                    });
                });

                // Draw Player (with cute hop bounce)
                const psy = baseScreenY - (player.row - cameraRow) * LANE_H - Math.sin(player.hopBounce * Math.PI) * 10;
                ctx.save();
                ctx.fillStyle = '#38BDF8';
                ctx.shadowColor = '#38BDF8';
                ctx.shadowBlur = 12;
                ctx.fillRect(player.x, psy + 6, PLAYER_SIZE, PLAYER_SIZE);
                // Eyes
                ctx.fillStyle = '#FFFFFF';
                ctx.fillRect(player.x + 5, psy + 10, 4, 4);
                ctx.fillRect(player.x + PLAYER_SIZE - 9, psy + 10, 4, 4);
                ctx.restore();

                // Idle warning banner
                const idleRemaining = 20 - Math.floor((Date.now() - idleTimer) / 1000);
                if (idleRemaining <= 5 && idleRemaining > 0 && !gameOver) {
                    ctx.fillStyle = '#F59E0B';
                    ctx.font = 'bold 14px sans-serif';
                    ctx.textAlign = 'center';
                    ctx.fillText('⚠️ HOP FORWARD: ' + idleRemaining + 's', canvas.width / 2, 35);
                }

                if (gameOver) {
                    ctx.fillStyle = 'rgba(9, 13, 22, 0.90)';
                    ctx.fillRect(0, 0, canvas.width, canvas.height);
                    ctx.fillStyle = '#EF4444';
                    ctx.font = 'bold 24px sans-serif';
                    ctx.textAlign = 'center';
                    let msg = 'CRASHED!';
                    if (deathReason === 'WATER') msg = '🌊 SPLASH! FELL IN RIVER!';
                    else if (deathReason === 'CAR') msg = '🚗 SQUASHED BY TRAFFIC!';
                    else if (deathReason === 'TRAIN') msg = '🚄 STRUCK BY BULLET TRAIN!';
                    else if (deathReason === 'IDLE') msg = '🦅 EAGLE SWOOPED! TOO SLOW!';
                    ctx.fillText(msg, canvas.width / 2, canvas.height / 2 - 25);
                    ctx.fillStyle = '#38BDF8';
                    ctx.font = 'bold 22px sans-serif';
                    ctx.fillText('Score: ' + maxRow + ' steps', canvas.width / 2, canvas.height / 2 + 15);
                    ctx.fillStyle = '#94A3B8';
                    ctx.font = '15px sans-serif';
                    ctx.fillText('Tap anywhere to try again', canvas.width / 2, canvas.height / 2 + 55);
                }
            }

            function loop() {
                update();
                draw();
                requestAnimationFrame(loop);
            }

            initGame();
            loop();
        </script>
    </body>
    </html>
    """.trimIndent()

    // 13. GAME: JETPACK RUSH (Jetpack Joyride Style)
    private fun getJetpackHtml(): String = """
    <!DOCTYPE html>
    <html>
    <head>
        <title>Jetpack Rush</title>
        $COMMON_HEAD
        <style>
            .container { display: flex; flex-direction: column; align-items: center; justify-content: center; height: calc(100% - 50px); padding: 4px; }
            #jetpack-canvas {
                background: #0B0F19;
                border: 2px solid #1E293B;
                border-radius: 12px;
                box-shadow: 0 0 20px rgba(56, 189, 248, 0.2);
                max-width: 96vw;
                height: 82vh;
                touch-action: none;
            }
        </style>
    </head>
    <body>
        <div class="hud">
            <div><span style="color:#94A3B8;">DIST: </span><span id="dist" class="badge">0m</span></div>
            <div><span style="color:#94A3B8;">COINS: </span><span id="coins" class="badge">0</span></div>
            <div><span style="color:#94A3B8;">BEST: </span><span id="best" class="badge">0m</span></div>
            <button class="btn" onclick="initGame()">Restart</button>
        </div>
        <div class="container">
            <canvas id="jetpack-canvas" width="380" height="580"></canvas>
        </div>

        <script>
            const canvas = document.getElementById('jetpack-canvas');
            const ctx = canvas.getContext('2d');

            let playerY = 480, playerVy = 0;
            let isThrusting = false;
            let distance = 0, coins = 0;
            let gameOver = false;
            let best = parseInt(SafeStorage.getItem('jetpack_best') || '0', 10);
            document.getElementById('best').innerText = best + 'm';

            let zappers = [];
            let coinPickups = [];
            let exhaustParticles = [];
            let lastSpawnDist = 0;

            const GRAVITY = 0.42;
            const THRUST = -0.75;
            const SPEED = 4.2;

            function initGame() {
                playerY = 480; playerVy = 0; isThrusting = false;
                distance = 0; coins = 0; gameOver = false;
                zappers = []; coinPickups = []; exhaustParticles = [];
                lastSpawnDist = 0;
                document.getElementById('dist').innerText = '0m';
                document.getElementById('coins').innerText = '0';
                AudioFX.play('tap');
            }

            function spawnObstacle() {
                const y = 80 + Math.random() * (canvas.height - 220);
                const isRotated = Math.random() < 0.35;
                zappers.push({
                    x: canvas.width + 30,
                    y,
                    len: 90,
                    angle: isRotated ? Math.PI / 4 : 0,
                    active: true
                });

                // Spawn a line of coins
                const coinY = 70 + Math.random() * (canvas.height - 200);
                for (let i = 0; i < 5; i++) {
                    coinPickups.push({
                        x: canvas.width + 120 + i * 28,
                        y: coinY + Math.sin(i * 0.5) * 20,
                        collected: false
                    });
                }
            }

            // Mobile Touch: Touch and hold anywhere to fire jetpack thruster
            document.body.addEventListener('touchstart', e => {
                if (e.target.closest('.hud')) return;
                e.preventDefault();
                if (gameOver) { initGame(); return; }
                isThrusting = true;
                AudioFX.init();
                AudioFX.vibrate(10);
            }, { passive: false });

            document.body.addEventListener('touchend', e => {
                if (e.target.closest('.hud')) return;
                isThrusting = false;
            });

            document.body.addEventListener('mousedown', e => {
                if (e.target.closest('.hud')) return;
                isThrusting = true;
            });
            document.body.addEventListener('mouseup', () => { isThrusting = false; });

            function update() {
                if (gameOver) return;

                distance += 1;
                document.getElementById('dist').innerText = Math.floor(distance / 5) + 'm';
                if (Math.floor(distance / 5) > best) {
                    best = Math.floor(distance / 5);
                    SafeStorage.setItem('jetpack_best', best);
                    document.getElementById('best').innerText = best + 'm';
                }

                if (distance - lastSpawnDist > 200) {
                    spawnObstacle();
                    lastSpawnDist = distance;
                }

                // Physics
                if (isThrusting) {
                    playerVy += THRUST;
                    playerVy = Math.max(-8, playerVy);
                    // Exhaust particles
                    exhaustParticles.push({
                        x: 60, y: playerY + 24,
                        vx: -3 - Math.random() * 2,
                        vy: 4 + Math.random() * 4,
                        radius: 3 + Math.random() * 3,
                        alpha: 1.0,
                        color: Math.random() < 0.5 ? '#F59E0B' : '#EF4444'
                    });
                } else {
                    playerVy += GRAVITY;
                    playerVy = Math.min(8, playerVy);
                }

                playerY += playerVy;
                // Floor and ceiling
                const floorY = canvas.height - 50;
                if (playerY > floorY) { playerY = floorY; playerVy = 0; }
                if (playerY < 50) { playerY = 50; playerVy = 0; }

                // Exhaust particles update
                exhaustParticles.forEach(p => {
                    p.x += p.vx; p.y += p.vy; p.alpha -= 0.05;
                });
                exhaustParticles = exhaustParticles.filter(p => p.alpha > 0);

                // Zappers update & collision
                zappers.forEach(z => {
                    z.x -= SPEED;
                    // Circle distance check on zapper ends
                    const x1 = z.x, y1 = z.y;
                    const x2 = z.x + Math.cos(z.angle) * z.len;
                    const y2 = z.y + Math.sin(z.angle) * z.len;

                    // Check player distance to segment
                    const px = 70, py = playerY + 12;
                    const l2 = (x2-x1)**2 + (y2-y1)**2;
                    let u = ((px-x1)*(x2-x1) + (py-y1)*(y2-y1)) / l2;
                    u = Math.max(0, Math.min(1, u));
                    const nx = x1 + u*(x2-x1), ny = y1 + u*(y2-y1);
                    if (Math.hypot(px - nx, py - ny) < 18) {
                        gameOver = true;
                        AudioFX.play('over');
                        AudioFX.vibrate(40);
                    }
                });
                zappers = zappers.filter(z => z.x > -120);

                // Coins update
                coinPickups.forEach(c => {
                    c.x -= SPEED;
                    if (!c.collected && Math.hypot(70 - c.x, (playerY + 12) - c.y) < 22) {
                        c.collected = true;
                        coins += 1;
                        document.getElementById('coins').innerText = coins;
                        AudioFX.play('score');
                        AudioFX.vibrate(10);
                    }
                });
                coinPickups = coinPickups.filter(c => c.x > -40);
            }

            function draw() {
                ctx.fillStyle = '#0B0F19';
                ctx.fillRect(0, 0, canvas.width, canvas.height);

                // Floor and ceiling
                ctx.fillStyle = '#1E293B';
                ctx.fillRect(0, 0, canvas.width, 40);
                ctx.fillRect(0, canvas.height - 40, canvas.width, 40);
                ctx.fillStyle = '#38BDF8';
                ctx.fillRect(0, 38, canvas.width, 2);
                ctx.fillRect(0, canvas.height - 40, canvas.width, 2);

                // Exhaust particles
                exhaustParticles.forEach(p => {
                    ctx.save();
                    ctx.globalAlpha = p.alpha;
                    ctx.fillStyle = p.color;
                    ctx.beginPath();
                    ctx.arc(p.x, p.y, p.radius, 0, Math.PI * 2);
                    ctx.fill();
                    ctx.restore();
                });

                // Coins
                coinPickups.forEach(c => {
                    if (c.collected) return;
                    ctx.save();
                    ctx.fillStyle = '#FBBF24';
                    ctx.shadowColor = '#FBBF24';
                    ctx.shadowBlur = 8;
                    ctx.beginPath();
                    ctx.arc(c.x, c.y, 8, 0, Math.PI * 2);
                    ctx.fill();
                    ctx.fillStyle = '#78350F';
                    ctx.font = 'bold 10px sans-serif';
                    ctx.textAlign = 'center';
                    ctx.textBaseline = 'middle';
                    ctx.fillText('$', c.x, c.y + 1);
                    ctx.restore();
                });

                // Zappers
                zappers.forEach(z => {
                    const x2 = z.x + Math.cos(z.angle) * z.len;
                    const y2 = z.y + Math.sin(z.angle) * z.len;
                    ctx.save();
                    ctx.strokeStyle = '#F59E0B';
                    ctx.lineWidth = 6;
                    ctx.shadowColor = '#EF4444';
                    ctx.shadowBlur = 12;
                    ctx.beginPath();
                    ctx.moveTo(z.x, z.y);
                    ctx.lineTo(x2, y2);
                    ctx.stroke();

                    // Electrodes
                    ctx.fillStyle = '#38BDF8';
                    ctx.beginPath(); ctx.arc(z.x, z.y, 9, 0, Math.PI*2); ctx.fill();
                    ctx.beginPath(); ctx.arc(x2, y2, 9, 0, Math.PI*2); ctx.fill();
                    ctx.restore();
                });

                // Player & Jetpack
                ctx.save();
                ctx.translate(65, playerY);
                // Jetpack unit
                ctx.fillStyle = '#64748B';
                ctx.fillRect(-10, 4, 8, 16);
                // Body
                ctx.fillStyle = '#38BDF8';
                ctx.shadowColor = '#38BDF8';
                ctx.shadowBlur = 8;
                ctx.fillRect(0, 0, 16, 24);
                // Visor
                ctx.fillStyle = '#F8FAFC';
                ctx.fillRect(8, 4, 8, 6);
                ctx.restore();

                if (gameOver) {
                    ctx.fillStyle = 'rgba(11, 15, 25, 0.88)';
                    ctx.fillRect(0, 0, canvas.width, canvas.height);
                    ctx.fillStyle = '#EF4444';
                    ctx.font = 'bold 30px sans-serif';
                    ctx.textAlign = 'center';
                    ctx.fillText('ZAPPED!', canvas.width / 2, canvas.height / 2 - 20);
                    ctx.fillStyle = '#38BDF8';
                    ctx.font = 'bold 22px sans-serif';
                    ctx.fillText(Math.floor(distance / 5) + 'm | ' + coins + ' Coins', canvas.width / 2, canvas.height / 2 + 20);
                    ctx.fillStyle = '#94A3B8';
                    ctx.font = '15px sans-serif';
                    ctx.fillText('Tap Restart to launch again', canvas.width / 2, canvas.height / 2 + 55);
                }
            }

            function loop() {
                update();
                draw();
                requestAnimationFrame(loop);
            }

            initGame();
            loop();
        </script>
    </body>
    </html>
    """.trimIndent()

    // 14. GAME: HILL CLIMB RACER
    private fun getHillClimbHtml(): String = """
    <!DOCTYPE html>
    <html>
    <head>
        <title>Hill Climb Racer</title>
        $COMMON_HEAD
        <style>
            .container { display: flex; flex-direction: column; align-items: center; justify-content: center; height: calc(100% - 50px); padding: 4px; }
            #hill-canvas {
                background: #090D16;
                border: 2px solid #1E293B;
                border-radius: 12px;
                box-shadow: 0 0 20px rgba(16, 185, 129, 0.2);
                max-width: 96vw;
                height: 72vh;
                touch-action: none;
            }
            .pedals {
                display: flex;
                justify-content: space-between;
                width: 100%;
                max-width: 380px;
                padding: 6px 12px;
                margin-top: 6px;
                touch-action: none;
            }
            .pedal {
                flex: 1;
                margin: 0 8px;
                height: 54px;
                border-radius: 14px;
                font-size: 15px;
                font-weight: 800;
                display: flex;
                flex-direction: column;
                align-items: center;
                justify-content: center;
                border: 2px solid;
                cursor: pointer;
            }
            .pedal-brake { background: rgba(239, 68, 68, 0.2); border-color: #EF4444; color: #EF4444; }
            .pedal-brake:active, .pedal-brake.active { background: #EF4444 !important; color: #FFFFFF !important; transform: scale(0.94); }
            .pedal-gas { background: rgba(16, 185, 129, 0.2); border-color: #10B981; color: #10B981; }
            .pedal-gas:active, .pedal-gas.active { background: #10B981 !important; color: #FFFFFF !important; transform: scale(0.94); }
        </style>
    </head>
    <body>
        <div class="hud">
            <div><span style="color:#94A3B8;">DIST: </span><span id="dist" class="badge">0m</span></div>
            <div><span style="color:#94A3B8;">FUEL: </span><span id="fuel" class="badge" style="color:#10B981;">100%</span></div>
            <div><span style="color:#94A3B8;">COINS: </span><span id="coins" class="badge">0</span></div>
            <button class="btn" onclick="initGame()">Restart</button>
        </div>
        <div class="container">
            <canvas id="hill-canvas" width="380" height="460"></canvas>
            <div class="pedals">
                <button id="btn-brake" class="pedal pedal-brake">BRAKE<small style="font-size:10px;font-weight:normal;opacity:0.8;">TILT BACK</small></button>
                <button id="btn-gas" class="pedal pedal-gas">GAS<small style="font-size:10px;font-weight:normal;opacity:0.8;">TILT FWD</small></button>
            </div>
        </div>
        <script>
            const canvas = document.getElementById('hill-canvas');
            const ctx = canvas.getContext('2d');

            let car = { x: 100, y: 200, vx: 0, vy: 0, angle: 0, vAngle: 0, fuel: 100 };
            let gasPressed = false;
            let brakePressed = false;
            let coins = 0;
            let bestDist = 0;
            let maxDist = 0;
            let gameOver = false;
            let deathReason = 'CRASH';
            let fuelPickups = [];
            let coinPickups = [];

            function groundY(wx) {
                return 320 + Math.sin(wx * 0.005) * 55 + Math.sin(wx * 0.014) * 28 + Math.cos(wx * 0.002) * 65;
            }

            function groundSlope(wx) {
                const delta = 2;
                return (groundY(wx + delta) - groundY(wx - delta)) / (2 * delta);
            }

            function initGame() {
                car = { x: 100, y: groundY(100) - 25, vx: 0, vy: 0, angle: 0, vAngle: 0, fuel: 100 };
                gasPressed = false;
                brakePressed = false;
                gameOver = false;
                maxDist = 0;
                fuelPickups = [];
                coinPickups = [];

                for (let wx = 350; wx < 10000; wx += 420) {
                    fuelPickups.push({ x: wx, y: groundY(wx) - 22, collected: false });
                }
                for (let wx = 180; wx < 10000; wx += 160) {
                    coinPickups.push({ x: wx, y: groundY(wx) - 20, collected: false });
                }
                updateHud();
            }

            function updateHud() {
                const d = Math.max(0, Math.floor((car.x - 100) / 10));
                if (d > maxDist) maxDist = d;
                if (maxDist > bestDist) bestDist = maxDist;
                document.getElementById('dist').textContent = maxDist + 'm';
                const fuelEl = document.getElementById('fuel');
                fuelEl.textContent = Math.max(0, Math.floor(car.fuel)) + '%';
                fuelEl.style.color = car.fuel < 25 ? '#EF4444' : '#10B981';
                document.getElementById('coins').textContent = coins;
            }

            function update() {
                if (gameOver) return;

                // Physics simulation
                const gravity = 0.38;
                car.vy += gravity;

                // Pedals input
                if (gasPressed) {
                    car.vx += Math.cos(car.angle) * 0.42;
                    car.vy += Math.sin(car.angle) * 0.42;
                    car.fuel = Math.max(0, car.fuel - 0.08);
                    car.vAngle -= 0.022; // Air tilt
                }
                if (brakePressed) {
                    car.vx -= Math.cos(car.angle) * 0.30;
                    car.vy -= Math.sin(car.angle) * 0.30;
                    car.vAngle += 0.025; // Air tilt back
                }

                // Passive fuel consumption
                car.fuel = Math.max(0, car.fuel - 0.015);

                // Damping
                car.vx *= 0.985;
                car.vy *= 0.99;
                car.vAngle *= 0.94;

                car.x += car.vx;
                car.y += car.vy;
                car.angle += car.vAngle;

                // Wheel contact points
                const wb = 22;
                const fx = car.x + Math.cos(car.angle) * wb;
                const fy = car.y + Math.sin(car.angle) * wb + 12;
                const rx = car.x - Math.cos(car.angle) * wb;
                const ry = car.y - Math.sin(car.angle) * wb + 12;

                const fgy = groundY(fx);
                const rgy = groundY(rx);

                let onGround = false;

                if (fy >= fgy) {
                    car.y -= (fy - fgy) * 0.6;
                    car.vy = Math.min(car.vy, 0);
                    onGround = true;
                }
                if (ry >= rgy) {
                    car.y -= (ry - rgy) * 0.6;
                    car.vy = Math.min(car.vy, 0);
                    onGround = true;
                }

                if (onGround) {
                    const targetAngle = Math.atan2(fgy - rgy, 2 * wb);
                    car.angle += (targetAngle - car.angle) * 0.22;
                }

                // Driver head collision check
                const hx = car.x - Math.sin(car.angle) * 18;
                const hy = car.y - Math.cos(car.angle) * 18;
                const hgy = groundY(hx);
                if (hy >= hgy - 4) {
                    gameOver = true;
                    deathReason = 'HEAD CRASH (FLIPPED)';
                    if (navigator.vibrate) navigator.vibrate(300);
                }

                // Out of gas check
                if (car.fuel <= 0 && Math.abs(car.vx) < 0.15 && Math.abs(car.vy) < 0.15) {
                    gameOver = true;
                    deathReason = 'OUT OF GAS';
                }

                // Coin pickups
                coinPickups.forEach(c => {
                    if (!c.collected && Math.hypot(car.x - c.x, car.y - c.y) < 28) {
                        c.collected = true;
                        coins += 1;
                        if (navigator.vibrate) navigator.vibrate(40);
                    }
                });

                // Fuel pickups
                fuelPickups.forEach(f => {
                    if (!f.collected && Math.hypot(car.x - f.x, car.y - f.y) < 32) {
                        f.collected = true;
                        car.fuel = 100;
                        if (navigator.vibrate) navigator.vibrate([60, 40, 80]);
                    }
                });

                updateHud();
            }

            function draw() {
                ctx.fillStyle = '#090D16';
                ctx.fillRect(0, 0, canvas.width, canvas.height);

                // Camera follow
                const camX = car.x - 120;
                const camY = car.y - 260;

                ctx.save();
                ctx.translate(-camX, -camY);

                // Draw background mountain silhouette
                ctx.fillStyle = '#111827';
                ctx.beginPath();
                ctx.moveTo(camX, camY + canvas.height);
                for (let wx = camX - 40; wx <= camX + canvas.width + 40; wx += 30) {
                    const my = 260 + Math.sin(wx * 0.002) * 80 + Math.cos(wx * 0.007) * 40;
                    ctx.lineTo(wx, my);
                }
                ctx.lineTo(camX + canvas.width + 40, camY + canvas.height);
                ctx.closePath();
                ctx.fill();

                // Draw ground
                ctx.fillStyle = '#10B981';
                ctx.beginPath();
                ctx.moveTo(camX - 20, camY + canvas.height + 100);
                for (let wx = camX - 20; wx <= camX + canvas.width + 30; wx += 10) {
                    ctx.lineTo(wx, groundY(wx));
                }
                ctx.lineTo(camX + canvas.width + 30, camY + canvas.height + 100);
                ctx.closePath();
                ctx.fill();

                // Ground soil under layer
                ctx.fillStyle = '#064E3B';
                ctx.beginPath();
                ctx.moveTo(camX - 20, camY + canvas.height + 100);
                for (let wx = camX - 20; wx <= camX + canvas.width + 30; wx += 10) {
                    ctx.lineTo(wx, groundY(wx) + 12);
                }
                ctx.lineTo(camX + canvas.width + 30, camY + canvas.height + 100);
                ctx.closePath();
                ctx.fill();

                // Draw coins
                coinPickups.forEach(c => {
                    if (!c.collected && c.x > camX - 20 && c.x < camX + canvas.width + 20) {
                        ctx.save();
                        ctx.fillStyle = '#FBBF24';
                        ctx.shadowColor = '#F59E0B';
                        ctx.shadowBlur = 8;
                        ctx.beginPath();
                        ctx.arc(c.x, c.y, 8, 0, Math.PI * 2);
                        ctx.fill();
                        ctx.fillStyle = '#92400E';
                        ctx.font = 'bold 9px sans-serif';
                        ctx.textAlign = 'center';
                        ctx.fillText('C', c.x, c.y + 3);
                        ctx.restore();
                    }
                });

                // Draw fuel canisters
                fuelPickups.forEach(f => {
                    if (!f.collected && f.x > camX - 20 && f.x < camX + canvas.width + 20) {
                        ctx.save();
                        ctx.fillStyle = '#EF4444';
                        ctx.fillRect(f.x - 9, f.y - 12, 18, 22);
                        ctx.fillStyle = '#FFFFFF';
                        ctx.fillRect(f.x - 4, f.y - 16, 8, 5);
                        ctx.font = 'bold 9px sans-serif';
                        ctx.textAlign = 'center';
                        ctx.fillText('GAS', f.x, f.y + 2);
                        ctx.restore();
                    }
                });

                // Draw Car
                ctx.save();
                ctx.translate(car.x, car.y);
                ctx.rotate(car.angle);

                // Chassis body
                ctx.fillStyle = '#38BDF8';
                ctx.shadowColor = '#38BDF8';
                ctx.shadowBlur = 10;
                ctx.fillRect(-24, -10, 48, 16);
                // Roof / Roll cage
                ctx.strokeStyle = '#94A3B8';
                ctx.lineWidth = 3;
                ctx.strokeRect(-14, -22, 26, 14);
                // Driver Helmet
                ctx.fillStyle = '#F59E0B';
                ctx.beginPath();
                ctx.arc(-2, -14, 6, 0, Math.PI * 2);
                ctx.fill();

                // Wheels
                ctx.fillStyle = '#1E293B';
                ctx.strokeStyle = '#64748B';
                ctx.lineWidth = 2;
                // Front wheel
                ctx.beginPath();
                ctx.arc(20, 8, 8, 0, Math.PI * 2);
                ctx.fill();
                ctx.stroke();
                // Rear wheel
                ctx.beginPath();
                ctx.arc(-20, 8, 8, 0, Math.PI * 2);
                ctx.fill();
                ctx.stroke();

                ctx.restore();
                ctx.restore();

                // Game Over banner
                if (gameOver) {
                    ctx.fillStyle = 'rgba(9, 13, 22, 0.90)';
                    ctx.fillRect(0, 0, canvas.width, canvas.height);
                    ctx.fillStyle = '#EF4444';
                    ctx.font = 'bold 24px sans-serif';
                    ctx.textAlign = 'center';
                    ctx.fillText(deathReason, canvas.width / 2, canvas.height / 2 - 25);
                    ctx.fillStyle = '#38BDF8';
                    ctx.font = 'bold 22px sans-serif';
                    ctx.fillText('Distance: ' + maxDist + 'm | ' + coins + ' Coins', canvas.width / 2, canvas.height / 2 + 15);
                    ctx.fillStyle = '#94A3B8';
                    ctx.font = '15px sans-serif';
                    ctx.fillText('Tap anywhere to drive again', canvas.width / 2, canvas.height / 2 + 55);
                }
            }

            function loop() {
                update();
                draw();
                requestAnimationFrame(loop);
            }

            // Controls setup
            const btnGas = document.getElementById('btn-gas');
            const btnBrake = document.getElementById('btn-brake');

            const startGas = (e) => { e.preventDefault(); gasPressed = true; btnGas.classList.add('active'); };
            const stopGas = (e) => { e.preventDefault(); gasPressed = false; btnGas.classList.remove('active'); };
            btnGas.addEventListener('touchstart', startGas);
            btnGas.addEventListener('touchend', stopGas);
            btnGas.addEventListener('mousedown', startGas);
            btnGas.addEventListener('mouseup', stopGas);

            const startBrake = (e) => { e.preventDefault(); brakePressed = true; btnBrake.classList.add('active'); };
            const stopBrake = (e) => { e.preventDefault(); brakePressed = false; btnBrake.classList.remove('active'); };
            btnBrake.addEventListener('touchstart', startBrake);
            btnBrake.addEventListener('touchend', stopBrake);
            btnBrake.addEventListener('mousedown', startBrake);
            btnBrake.addEventListener('mouseup', stopBrake);

            // Tap screen to restart when game over
            canvas.addEventListener('click', () => { if (gameOver) initGame(); });
            canvas.addEventListener('touchstart', () => { if (gameOver) initGame(); });

            window.addEventListener('keydown', (e) => {
                if (e.key === 'ArrowRight' || e.key === 'd') gasPressed = true;
                if (e.key === 'ArrowLeft' || e.key === 'a') brakePressed = true;
                if (e.key === ' ' && gameOver) initGame();
            });
            window.addEventListener('keyup', (e) => {
                if (e.key === 'ArrowRight' || e.key === 'd') gasPressed = false;
                if (e.key === 'ArrowLeft' || e.key === 'a') brakePressed = false;
            });

            initGame();
            loop();
        </script>
    </body>
    </html>
    """.trimIndent()

    // 15. GAME: ALTO'S MOUNTAIN SLIDE
    private fun getAltosSlideHtml(): String = """
    <!DOCTYPE html>
    <html>
    <head>
        <title>Alto's Mountain Slide</title>
        $COMMON_HEAD
        <style>
            .container { display: flex; flex-direction: column; align-items: center; justify-content: center; height: calc(100% - 50px); padding: 4px; }
            #alto-canvas {
                background: linear-gradient(180deg, #1E1B4B 0%, #4338CA 40%, #BE185D 75%, #F97316 100%);
                border: 2px solid #312E81;
                border-radius: 12px;
                box-shadow: 0 0 24px rgba(244, 63, 94, 0.25);
                max-width: 96vw;
                height: 84vh;
                touch-action: none;
            }
        </style>
    </head>
    <body>
        <div class="hud">
            <div><span style="color:#94A3B8;">DIST: </span><span id="dist" class="badge">0m</span></div>
            <div><span style="color:#94A3B8;">FLIPS: </span><span id="flips" class="badge" style="color:#F43F5E;">0</span></div>
            <div><span style="color:#94A3B8;">COINS: </span><span id="coins" class="badge">0</span></div>
            <button class="btn" onclick="initGame()">Restart</button>
        </div>
        <div class="container">
            <canvas id="alto-canvas" width="380" height="520"></canvas>
        </div>
        <script>
            const canvas = document.getElementById('alto-canvas');
            const ctx = canvas.getContext('2d');

            let rider = { x: 100, y: 200, vx: 5, vy: 0, angle: 0, vAngle: 0, inAir: false, flipsCount: 0 };
            let touching = false;
            let coins = 0;
            let bestDist = 0;
            let maxDist = 0;
            let totalFlips = 0;
            let comboMult = 1;
            let gameOver = false;
            let scarf = [];
            let obstacles = [];
            let coinPickups = [];

            function groundY(wx) {
                return 360 + Math.sin(wx * 0.004) * 65 + Math.sin(wx * 0.012) * 35;
            }

            function groundSlope(wx) {
                const delta = 2;
                return (groundY(wx + delta) - groundY(wx - delta)) / (2 * delta);
            }

            function initGame() {
                rider = { x: 100, y: groundY(100) - 15, vx: 4.8, vy: 0, angle: 0, vAngle: 0, inAir: false, flipsCount: 0 };
                touching = false;
                comboMult = 1;
                gameOver = false;
                maxDist = 0;
                scarf = [];
                for (let i = 0; i < 14; i++) {
                    scarf.push({ x: rider.x - i * 4, y: rider.y });
                }
                obstacles = [];
                coinPickups = [];
                for (let wx = 500; wx < 15000; wx += 350 + Math.random() * 250) {
                    obstacles.push({ x: wx, y: groundY(wx), width: 22, height: 20 });
                }
                for (let wx = 280; wx < 15000; wx += 190) {
                    coinPickups.push({ x: wx, y: groundY(wx) - 30, collected: false });
                }
                updateHud();
            }

            function updateHud() {
                const d = Math.max(0, Math.floor((rider.x - 100) / 10));
                if (d > maxDist) maxDist = d;
                if (maxDist > bestDist) bestDist = maxDist;
                document.getElementById('dist').textContent = maxDist + 'm';
                document.getElementById('flips').textContent = totalFlips;
                document.getElementById('coins').textContent = coins;
            }

            function update() {
                if (gameOver) return;

                // Physics
                const gy = groundY(rider.x);
                const slope = groundSlope(rider.x);
                const slopeAngle = Math.atan(slope);

                if (rider.y >= gy - 6) {
                    // On ground
                    if (rider.inAir) {
                        // Landing check
                        const diff = Math.abs(Math.sin(rider.angle - slopeAngle));
                        if (diff > 0.6) {
                            // Crash on head
                            gameOver = true;
                            if (navigator.vibrate) navigator.vibrate(300);
                        } else {
                            // Perfect landing
                            if (rider.flipsCount > 0) {
                                totalFlips += rider.flipsCount;
                                rider.vx += rider.flipsCount * 2.2;
                                comboMult++;
                                if (navigator.vibrate) navigator.vibrate([40, 30, 60]);
                            }
                        }
                        rider.flipsCount = 0;
                    }
                    rider.inAir = false;
                    rider.y = gy - 6;
                    rider.vy = 0;
                    rider.angle = slopeAngle;

                    // Downhill acceleration
                    rider.vx += Math.sin(slopeAngle) * 0.28;
                    rider.vx = Math.max(3.2, Math.min(rider.vx * 0.998, 14));
                } else {
                    // Airborne
                    rider.inAir = true;
                    rider.vy += 0.36; // Gravity
                    if (touching) {
                        // Backflip rotation
                        rider.angle -= 0.095;
                        if (Math.abs(rider.angle) > (rider.flipsCount + 1) * Math.PI * 1.8) {
                            rider.flipsCount++;
                        }
                    }
                }

                rider.x += rider.vx;
                rider.y += rider.vy;

                // Scarf trailing physics
                if (scarf.length > 0) {
                    scarf[0] = { x: rider.x - Math.cos(rider.angle) * 8, y: rider.y - 12 };
                    for (let i = 1; i < scarf.length; i++) {
                        const target = scarf[i - 1];
                        scarf[i].x += (target.x - scarf[i].x) * 0.45;
                        scarf[i].y += (target.y - scarf[i].y) * 0.45 + 0.3;
                    }
                }

                // Obstacle collision
                obstacles.forEach(obs => {
                    if (Math.abs(rider.x - obs.x) < 14 && Math.abs(rider.y - obs.y) < 16 && !rider.inAir) {
                        gameOver = true;
                        if (navigator.vibrate) navigator.vibrate(300);
                    }
                });

                // Coin collection
                coinPickups.forEach(c => {
                    if (!c.collected && Math.hypot(rider.x - c.x, rider.y - c.y) < 26) {
                        c.collected = true;
                        coins += 1;
                        if (navigator.vibrate) navigator.vibrate(40);
                    }
                });

                updateHud();
            }

            function draw() {
                ctx.clearRect(0, 0, canvas.width, canvas.height);

                const camX = rider.x - 110;
                const camY = rider.y - 280;

                ctx.save();
                ctx.translate(-camX, -camY);

                // Distant mountains
                ctx.fillStyle = '#312E81';
                ctx.beginPath();
                ctx.moveTo(camX, camY + canvas.height);
                for (let wx = camX - 40; wx <= camX + canvas.width + 40; wx += 25) {
                    const my = 280 + Math.sin(wx * 0.002) * 90 + Math.cos(wx * 0.006) * 50;
                    ctx.lineTo(wx, my);
                }
                ctx.lineTo(camX + canvas.width + 40, camY + canvas.height);
                ctx.closePath();
                ctx.fill();

                // Ground snow
                ctx.fillStyle = '#FFFFFF';
                ctx.beginPath();
                ctx.moveTo(camX - 20, camY + canvas.height + 100);
                for (let wx = camX - 20; wx <= camX + canvas.width + 30; wx += 10) {
                    ctx.lineTo(wx, groundY(wx));
                }
                ctx.lineTo(camX + canvas.width + 30, camY + canvas.height + 100);
                ctx.closePath();
                ctx.fill();

                // Under snow gradient
                ctx.fillStyle = '#E2E8F0';
                ctx.beginPath();
                ctx.moveTo(camX - 20, camY + canvas.height + 100);
                for (let wx = camX - 20; wx <= camX + canvas.width + 30; wx += 10) {
                    ctx.lineTo(wx, groundY(wx) + 8);
                }
                ctx.lineTo(camX + canvas.width + 30, camY + canvas.height + 100);
                ctx.closePath();
                ctx.fill();

                // Obstacles
                ctx.fillStyle = '#475569';
                obstacles.forEach(obs => {
                    if (obs.x > camX - 20 && obs.x < camX + canvas.width + 20) {
                        ctx.beginPath();
                        ctx.moveTo(obs.x - 10, obs.y);
                        ctx.lineTo(obs.x, obs.y - obs.height);
                        ctx.lineTo(obs.x + 10, obs.y);
                        ctx.closePath();
                        ctx.fill();
                    }
                });

                // Coins
                coinPickups.forEach(c => {
                    if (!c.collected && c.x > camX - 20 && c.x < camX + canvas.width + 20) {
                        ctx.save();
                        ctx.fillStyle = '#FBBF24';
                        ctx.shadowColor = '#F59E0B';
                        ctx.shadowBlur = 8;
                        ctx.beginPath();
                        ctx.arc(c.x, c.y, 7, 0, Math.PI * 2);
                        ctx.fill();
                        ctx.restore();
                    }
                });

                // Scarf
                if (scarf.length > 1) {
                    ctx.strokeStyle = '#EF4444';
                    ctx.lineWidth = 3.5;
                    ctx.lineCap = 'round';
                    ctx.beginPath();
                    ctx.moveTo(scarf[0].x, scarf[0].y);
                    for (let i = 1; i < scarf.length; i++) {
                        ctx.lineTo(scarf[i].x, scarf[i].y);
                    }
                    ctx.stroke();
                }

                // Snowboarder
                ctx.save();
                ctx.translate(rider.x, rider.y);
                ctx.rotate(rider.angle);

                // Snowboard
                ctx.fillStyle = '#F43F5E';
                ctx.shadowColor = '#F43F5E';
                ctx.shadowBlur = 8;
                ctx.beginPath();
                ctx.roundRect(-16, -2, 32, 4, 2);
                ctx.fill();

                // Rider body
                ctx.fillStyle = '#F8FAFC';
                ctx.fillRect(-4, -14, 8, 12);
                // Rider hood
                ctx.fillStyle = '#F43F5E';
                ctx.beginPath();
                ctx.arc(0, -17, 5, 0, Math.PI * 2);
                ctx.fill();

                ctx.restore();
                ctx.restore();

                // Game Over banner
                if (gameOver) {
                    ctx.fillStyle = 'rgba(15, 23, 42, 0.88)';
                    ctx.fillRect(0, 0, canvas.width, canvas.height);
                    ctx.fillStyle = '#EF4444';
                    ctx.font = 'bold 26px sans-serif';
                    ctx.textAlign = 'center';
                    ctx.fillText('WIPEOUT!', canvas.width / 2, canvas.height / 2 - 25);
                    ctx.fillStyle = '#F43F5E';
                    ctx.font = 'bold 20px sans-serif';
                    ctx.fillText(maxDist + 'm | ' + totalFlips + ' Backflips', canvas.width / 2, canvas.height / 2 + 15);
                    ctx.fillStyle = '#94A3B8';
                    ctx.font = '15px sans-serif';
                    ctx.fillText('Tap anywhere to slide again', canvas.width / 2, canvas.height / 2 + 55);
                }
            }

            function loop() {
                update();
                draw();
                requestAnimationFrame(loop);
            }

            // Touch / Jump / Backflip controls
            const onTouchStart = (e) => {
                e.preventDefault();
                if (gameOver) {
                    initGame();
                    return;
                }
                touching = true;
                if (!rider.inAir) {
                    rider.vy = -8.6;
                    rider.inAir = true;
                    if (navigator.vibrate) navigator.vibrate(30);
                }
            };
            const onTouchEnd = (e) => {
                e.preventDefault();
                touching = false;
            };

            canvas.addEventListener('touchstart', onTouchStart);
            canvas.addEventListener('touchend', onTouchEnd);
            canvas.addEventListener('mousedown', onTouchStart);
            canvas.addEventListener('mouseup', onTouchEnd);

            window.addEventListener('keydown', (e) => {
                if (e.key === ' ' || e.key === 'ArrowUp') {
                    if (gameOver) initGame();
                    else {
                        touching = true;
                        if (!rider.inAir) rider.vy = -8.6;
                    }
                }
            });
            window.addEventListener('keyup', (e) => {
                if (e.key === ' ' || e.key === 'ArrowUp') touching = false;
            });

            initGame();
            loop();
        </script>
    </body>
    </html>
    """.trimIndent()
}


