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
}
