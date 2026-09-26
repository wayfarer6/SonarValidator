// react plugin for creating vector maps
import { VectorMap } from "@react-jvectormap/core";
import { worldMill } from "@react-jvectormap/world";

// Define the component props
interface CountryMapProps {
  mapColor?: string;
}

const CountryMap: React.FC<CountryMapProps> = ({ mapColor }) => {
  return (
    <VectorMap
      map={worldMill}
      backgroundColor="transparent"
      markerStyle={{
        initial: {
          fill: "#465FFF",
          // ⚠️ `as any` 를 쓰지 않습니다.
          //   CI 의 `npm run lint` 가 @typescript-eslint/no-explicit-any 로
          //   실패합니다. r 은 jvectormap 고유 속성이라 SVG CSS 타입에 없으므로,
          //   타입 단언 대신 Record 로 "추가 속성 허용" 을 명시합니다.
          //   (any 는 오타도 통과시키지만, 이 형태는 키 이름을 검사합니다)
          ...({ r: 4 } as Record<string, number | string>),
        },
      }}
      markersSelectable={true}
      markers={[
        {
          latLng: [37.2580397, -104.657039],
          name: "United States",
          style: {
            fill: "#465FFF",
            borderWidth: 1,
            borderColor: "white",
            stroke: "#383f47",
          },
        },
        {
          latLng: [20.7504374, 73.7276105],
          name: "India",
          style: { fill: "#465FFF", borderWidth: 1, borderColor: "white" },
        },
        {
          latLng: [53.613, -11.6368],
          name: "United Kingdom",
          style: { fill: "#465FFF", borderWidth: 1, borderColor: "white" },
        },
        {
          latLng: [-25.0304388, 115.2092761],
          name: "Sweden",
          style: {
            fill: "#465FFF",
            borderWidth: 1,
            borderColor: "white",
            strokeOpacity: 0,
          },
        },
      ]}
      zoomOnScroll={false}
      zoomMax={12}
      zoomMin={1}
      zoomAnimate={true}
      zoomStep={1.5}
      regionStyle={{
        initial: {
          fill: mapColor || "#D0D5DD",
          fillOpacity: 1,
          fontFamily: "Outfit",
          stroke: "none",
          strokeWidth: 0,
          strokeOpacity: 0,
        },
        hover: {
          fillOpacity: 0.7,
          cursor: "pointer",
          fill: "#465fff",
          stroke: "none",
        },
        selected: {
          fill: "#465FFF",
        },
        selectedHover: {},
      }}
      regionLabelStyle={{
        initial: {
          fill: "#35373e",
          fontWeight: 500,
          fontSize: "13px",
          stroke: "none",
        },
        hover: {},
        selected: {},
        selectedHover: {},
      }}
    />
  );
};

export default CountryMap;
