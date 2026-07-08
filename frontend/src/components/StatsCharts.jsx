import { useId, useRef, useState } from 'react'
import { TIME_RANGE_OPTIONS } from './chartConfig'

function parseBucket(bucket) {
  const date = new Date(bucket)
  return Number.isNaN(date.getTime()) ? null : date
}

function formatAxisLabel(bucket, range) {
  const date = parseBucket(bucket)
  if (!date) return bucket
  if (range === '24h') {
    return new Intl.DateTimeFormat('vi-VN', {
      day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit',
      hour12: false,
    }).format(date)
  }
  return new Intl.DateTimeFormat('vi-VN', {
    day: '2-digit', month: '2-digit',
    hour12: false,
  }).format(date)
}

function formatTooltipLabel(bucket) {
  const date = parseBucket(bucket)
  if (!date) return bucket
  return new Intl.DateTimeFormat('vi-VN', {
    dateStyle: 'medium',
    timeStyle: 'short',
    hour12: false,
  }).format(date)
}

function buildLinePath(data, width, height, pad, bottomPad) {
  if (!data.length) return ''
  const max = Math.max(...data.map(d => d.count), 1)
  const innerWidth = width - pad * 2
  const innerHeight = height - pad - bottomPad
  return data.map((point, index) => {
    const x = pad + (index * innerWidth) / Math.max(data.length - 1, 1)
    const y = pad + innerHeight - (point.count / max) * innerHeight
    return `${index === 0 ? 'M' : 'L'} ${x} ${y}`
  }).join(' ')
}

function polar(cx, cy, r, angle) {
  return {
    x: cx + r * Math.cos(angle),
    y: cy + r * Math.sin(angle),
  }
}

function slicePath(cx, cy, r, startAngle, endAngle) {
  const start = polar(cx, cy, r, startAngle)
  const end = polar(cx, cy, r, endAngle)
  const largeArc = endAngle - startAngle > Math.PI ? 1 : 0
  return [
    `M ${cx} ${cy}`,
    `L ${start.x} ${start.y}`,
    `A ${r} ${r} 0 ${largeArc} 1 ${end.x} ${end.y}`,
    'Z',
  ].join(' ')
}

function colorFor(index) {
  const colors = ['#63b3ed', '#68d391', '#f6ad55', '#fc8181', '#b794f4', '#4fd1c5', '#f6e05e']
  return colors[index % colors.length]
}

function ChartTooltip({ tooltip }) {
  if (!tooltip) return null
  return (
    <div
      className="chart-tooltip"
      style={{ left: tooltip.x, top: tooltip.y, borderColor: tooltip.color || '#63b3ed' }}
    >
      <div className="chart-tooltip-kicker">{tooltip.kicker}</div>
      <div className="chart-tooltip-title">{tooltip.title}</div>
      <div className="chart-tooltip-value">{tooltip.value}</div>
      {tooltip.meta && <div className="chart-tooltip-meta">{tooltip.meta}</div>}
    </div>
  )
}

export function TimeRangeToggle({ value, onChange }) {
  return (
    <div className="chart-range-group" role="tablist" aria-label="Time range filter">
      {TIME_RANGE_OPTIONS.map(option => (
        <button
          key={option.value}
          type="button"
          className={`chart-range-btn ${value === option.value ? 'active' : ''}`}
          onClick={() => onChange(option.value)}
          aria-pressed={value === option.value}
        >
          {option.label}
        </button>
      ))}
    </div>
  )
}

export function LineChartCard({ title, subtitle, data, color = '#63b3ed', timeRange = '24h', loading = false }) {
  const width = 760
  const height = 280
  const pad = 30
  const bottomPad = 44
  const path = buildLinePath(data, width, height, pad, bottomPad)
  const max = Math.max(...data.map(d => d.count), 1)
  const fillId = useId()
  const clipId = useId()
  const shellRef = useRef(null)
  const [tooltip, setTooltip] = useState(null)

  const showTooltip = (event, point, index) => {
    const rect = shellRef.current?.getBoundingClientRect()
    if (!rect) return
    const x = Math.min(Math.max(event.clientX - rect.left, 116), rect.width - 116)
    const y = Math.max(event.clientY - rect.top - 14, 72)
    const share = Math.round((point.count / max) * 100)
    setTooltip({
      x,
      y,
      color,
      kicker: `Điểm #${index + 1}`,
      title: formatTooltipLabel(point.bucket),
      value: `${point.count.toLocaleString('vi-VN')} sự kiện`,
      meta: `${share}% so với đỉnh`,
    })
  }

  return (
    <div className="card chart-card">
      <div className="card-header">
        <span className="card-title">{title}</span>
        <span className="card-count">{subtitle}</span>
      </div>
      {loading ? (
        <div className="loading">Đang tải…</div>
      ) : data.length === 0 ? (
        <div className="empty">Chưa có dữ liệu</div>
      ) : (
        <div ref={shellRef} className="chart-shell" onMouseLeave={() => setTooltip(null)}>
          <div className="chart-scroll">
            <svg viewBox={`0 0 ${width} ${height}`} className="chart-svg">
            <defs>
              <linearGradient id={fillId} x1="0" x2="0" y1="0" y2="1">
                <stop offset="0%" stopColor={color} stopOpacity="0.25" />
                <stop offset="100%" stopColor={color} stopOpacity="0.02" />
              </linearGradient>
              <clipPath id={clipId}>
                <rect x={pad} y={pad} width={width - pad * 2} height={height - pad - bottomPad} rx="10" />
              </clipPath>
            </defs>
            <line x1={pad} y1={height - bottomPad} x2={width - pad} y2={height - bottomPad} stroke="#2d3748" strokeWidth="1" />
            <line x1={pad} y1={pad} x2={pad} y2={height - pad} stroke="#2d3748" strokeWidth="1" />
            <g clipPath={`url(#${clipId})`}>
              <path
                d={`${path} L ${width - pad} ${height - bottomPad} L ${pad} ${height - bottomPad} Z`}
                fill={`url(#${fillId})`}
              />
              <path d={path} fill="none" stroke={color} strokeWidth="3" strokeLinejoin="round" strokeLinecap="round" />
              {data.map((point, index) => {
                const x = pad + (index * (width - pad * 2)) / Math.max(data.length - 1, 1)
                const y = pad + (height - pad - bottomPad) - (point.count / max) * (height - pad - bottomPad)
                const isTick = index === 0 || index === data.length - 1 || index % Math.ceil(data.length / 4) === 0
                return (
                  <g key={`${point.bucket}-${index}`}>
                    <circle
                      cx={x}
                      cy={y}
                      r="5"
                      fill={color}
                      stroke="#0f1117"
                      strokeWidth="2"
                      onMouseEnter={(event) => showTooltip(event, point, index)}
                      onMouseMove={(event) => showTooltip(event, point, index)}
                      onFocus={(event) => showTooltip(event, point, index)}
                      style={{ cursor: 'pointer' }}
                      tabIndex={0}
                    />
                    {isTick && (
                      <text x={x} y={height - 14} fill="#a0aec0" fontSize="11" textAnchor="middle">
                        {formatAxisLabel(point.bucket, timeRange)}
                      </text>
                    )}
                  </g>
                )
              })}
            </g>
            <text x={14} y={18} fill="#a0aec0" fontSize="11">max {max.toLocaleString('vi-VN')}</text>
          </svg>
          <ChartTooltip tooltip={tooltip} />
        </div>
        </div>
      )}
    </div>
  )
}

export function PieChartCard({ title, subtitle, data, loading = false }) {
  const width = 340
  const height = 240
  const cx = 120
  const cy = 110
  const radius = 74
  const total = data.reduce((sum, item) => sum + item.count, 0) || 1
  const shellRef = useRef(null)
  const [tooltip, setTooltip] = useState(null)

  const pieSlices = data.reduce((acc, item, index) => {
    const angle = (item.count / total) * Math.PI * 2
    const start = acc.cursor
    const end = start + angle
    acc.slices.push({ item, index, start, end, color: colorFor(index) })
    return { cursor: end, slices: acc.slices }
  }, { cursor: -Math.PI / 2, slices: [] })

  const showTooltip = (event, item) => {
    const rect = shellRef.current?.getBoundingClientRect()
    if (!rect) return
    const x = Math.min(Math.max(event.clientX - rect.left, 110), rect.width - 110)
    const y = Math.max(event.clientY - rect.top - 14, 72)
    setTooltip({
      x,
      y,
      color: item.color,
      kicker: 'Tỷ lệ eventType',
      title: item.label,
      value: `${item.count.toLocaleString('vi-VN')} lần xuất hiện`,
      meta: `${((item.count / total) * 100).toFixed(1)}% tổng số`,
    })
  }

  return (
    <div className="card chart-card">
      <div className="card-header">
        <span className="card-title">{title}</span>
        <span className="card-count">{subtitle}</span>
      </div>
      {loading ? (
        <div className="loading">Đang tải…</div>
      ) : data.length === 0 ? (
        <div className="empty">Chưa có dữ liệu</div>
      ) : (
        <div ref={shellRef} className="chart-shell" onMouseLeave={() => setTooltip(null)}>
          <div className="pie-layout">
            <svg viewBox={`0 0 ${width} ${height}`} className="chart-svg pie-svg">
              {pieSlices.slices.map(({ item, start, end, color }) => (
                <path
                  key={item.label}
                  d={slicePath(cx, cy, radius, start, end)}
                  fill={color}
                  stroke="#0f1117"
                  strokeWidth="2"
                  onMouseEnter={(event) => showTooltip(event, { ...item, color })}
                  onMouseMove={(event) => showTooltip(event, { ...item, color })}
                  onFocus={(event) => showTooltip(event, { ...item, color })}
                  style={{ cursor: 'pointer' }}
                  tabIndex={0}
                />
              ))}
              <circle cx={cx} cy={cy} r="38" fill="#0f1117" />
              <text x={cx} y={cy - 3} fill="#e2e8f0" fontSize="16" fontWeight="700" textAnchor="middle">
                {total.toLocaleString('vi-VN')}
              </text>
              <text x={cx} y={cy + 15} fill="#718096" fontSize="11" textAnchor="middle">logs</text>
            </svg>
            <div className="chart-legend">
              {data.map((item, index) => (
                <div key={item.label} className="chart-legend-item">
                  <span style={{ width: 10, height: 10, borderRadius: 999, background: colorFor(index), display: 'inline-block', flexShrink: 0 }} />
                  <span className="chart-legend-label" title={item.label}>{item.label}</span>
                  <span className="mono chart-legend-count">{item.count.toLocaleString('vi-VN')}</span>
                </div>
              ))}
            </div>
          </div>
          <ChartTooltip tooltip={tooltip} />
        </div>
      )}
    </div>
  )
}