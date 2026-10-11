import { useNavigate } from "react-router"; // go back 이 그거 이전 페이지로 이동하는 훅

const useGoBack = () => {
  const navigate = useNavigate();

  const goBack = () => {
    if (window.history.state && window.history.state.idx > 0) {
      navigate(-1); // Go back to the previous page
    } else {
      navigate("/"); // Redirect to home if no history exists
    }
  };

  return goBack;
};

export default useGoBack;
